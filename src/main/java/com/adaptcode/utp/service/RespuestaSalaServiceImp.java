package com.adaptcode.utp.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.adaptcode.utp.dto.request.RespuestaSalaRequest;
import com.adaptcode.utp.dto.response.RespuestaSalaResponse;
import com.adaptcode.utp.exception.ResourceNotFoundException;
import com.adaptcode.utp.exception.SolicitudInvalidaException;
import com.adaptcode.utp.model.AlternativaDocente;
import com.adaptcode.utp.model.EstadoSala;
import com.adaptcode.utp.model.ParticipacionSala;
import com.adaptcode.utp.model.PreguntaDocente;
import com.adaptcode.utp.model.RespuestaSala;
import com.adaptcode.utp.model.Sala;
import com.adaptcode.utp.repository.AlternativaDocenteRepository;
import com.adaptcode.utp.repository.AnulacionPreguntaRepository;
import com.adaptcode.utp.repository.ParticipacionSalaRepository;
import com.adaptcode.utp.repository.PreguntaDocenteRepository;
import com.adaptcode.utp.repository.QuizItemRepository;
import com.adaptcode.utp.repository.RespuestaSalaRepository;
import com.adaptcode.utp.repository.SalaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RespuestaSalaServiceImp implements RespuestaSalaService {

	private final RespuestaSalaRepository respuestaSalaRepository;
	private final ParticipacionSalaRepository participacionSalaRepository;
	private final SalaRepository salaRepository;
	private final QuizItemRepository quizItemRepository;
	private final PreguntaDocenteRepository preguntaDocenteRepository;
	private final AnulacionPreguntaRepository anulacionPreguntaRepository;
	private final AlternativaDocenteRepository alternativaDocenteRepository;
	private final RankingCache rankingCache;
	private final SalaEventoService salaEventoService;

	/**
	 * Margen de red para no rechazar por milisegundos a un cliente que envió su
	 * respuesta justo antes de que venciera el plazo.
	 */
	@Value("${sala.gracia-tiempo-ms:2000}")
	private long graciaMs;

	@Override
	@Transactional
	public RespuestaSalaResponse registrar(Long salaId, RespuestaSalaRequest request) {
		Sala sala = buscarSala(salaId);
		if (sala.getEstado() != EstadoSala.EN_CURSO) {
			throw new SolicitudInvalidaException(
					"Solo se aceptan respuestas con la pregunta en curso.");
		}
		if (sala.getPreguntaActual() == null
				|| !sala.getPreguntaActual().getId().equals(request.preguntaId())) {
			throw new SolicitudInvalidaException(
					"La pregunta indicada no es la que está en curso.");
		}
		ParticipacionSala participacion = participacionSalaRepository
				.findById(request.participacionId())
				.filter(p -> salaId.equals(p.getSala().getId()))
				.orElseThrow(() -> new ResourceNotFoundException(
						"La participación no existe."));
		if (participacion.isExpulsado()) {
			throw new SolicitudInvalidaException(
					"La participación fue expulsada de la sala.");
		}
		PreguntaDocente pregunta = preguntaDocenteRepository
				.findById(request.preguntaId())
				.filter(PreguntaDocente::isActiva)
				.orElseThrow(() -> new ResourceNotFoundException(
						"La pregunta no existe."));
		if (!quizItemRepository.existsByQuiz_IdAndPregunta_Id(
				sala.getQuizOrigen().getId(), pregunta.getId())) {
			throw new SolicitudInvalidaException(
					"La pregunta no pertenece al quiz de la sala.");
		}
		if (anulacionPreguntaRepository.existsBySala_IdAndPregunta_Id(
				salaId, pregunta.getId())) {
			throw new SolicitudInvalidaException(
					"La pregunta fue anulada y no recibe respuestas.");
		}
		if (respuestaSalaRepository.existsByParticipacion_IdAndPregunta_Id(
				participacion.getId(), pregunta.getId())) {
			throw new SolicitudInvalidaException(
					"Cada pregunta admite un único envío definitivo.");
		}
		boolean omitida = Boolean.TRUE.equals(request.omitida());
		AlternativaDocente elegida = null;
		boolean acierto = false;
		if (!omitida) {
			if (request.alternativaId() == null) {
				throw new SolicitudInvalidaException(
						"Debe indicar la alternativa seleccionada o marcar la pregunta como omitida.");
			}
			elegida = alternativaDocenteRepository.findById(request.alternativaId())
					.filter(a -> a.getPregunta().getId().equals(pregunta.getId()))
					.orElseThrow(() -> new SolicitudInvalidaException(
							"La alternativa no pertenece a la pregunta."));
			// La calificación es del servidor: el campo `acierto` del cliente se ignora.
			acierto = elegida.isCorrecta();
		}
		RespuestaSala respuesta = new RespuestaSala(participacion, pregunta,
				acierto, omitida);
		respuesta.setAlternativaElegida(elegida);
		respuesta.setTiempoMs(medirTiempoVigente(sala));
		RespuestaSala guardada = respuestaSalaRepository.save(respuesta);
		rankingCache.invalidar(salaId);
		long respondidas = respuestaSalaRepository
				.countByParticipacion_Sala_IdAndPregunta_Id(salaId, pregunta.getId());
		// Solo el contador agregado: quién respondió y si acertó no se difunde.
		salaEventoService.publicarTrasCommit(salaId,
				SalaEventoService.EVENTO_RESPUESTAS,
				Map.of("salaId", salaId, "preguntaId", pregunta.getId(),
						"respuestas", respondidas));
		return aResponse(guardada, sala);
	}

	/**
	 * Tiempo medido en el servidor desde que se habilitó la pregunta. Un cliente
	 * no puede declarar un tiempo menor y una respuesta enviada después del
	 * plazo (más el margen de red) se rechaza en lugar de recortar el tiempo.
	 */
	private long medirTiempoVigente(Sala sala) {
		long limiteMs = sala.segundosLimitePregunta() * 1000L;
		if (sala.getPreguntaIniciadaEn() == null) {
			// Sala creada antes de que existiera el cronómetro del servidor: no
			// se puede juzgar el instante, pero tampoco se regala tiempo cero.
			return limiteMs;
		}
		long transcurrido = Math.max(0L, Duration.between(
				sala.getPreguntaIniciadaEn(), Instant.now()).toMillis());
		long margen = Math.max(0L, graciaMs);
		if (transcurrido > limiteMs + margen) {
			throw new SolicitudInvalidaException(
					"El tiempo para responder esta pregunta ya venció.");
		}
		return Math.min(transcurrido, limiteMs);
	}

	@Override
	@Transactional(readOnly = true)
	public RespuestaSalaResponse mostrarPorId(Long salaId, Long id) {
		validarIdentificador(id);
		Sala sala = buscarSala(salaId);
		RespuestaSala respuesta = respuestaSalaRepository.findById(id)
				.filter(r -> salaId.equals(
						r.getParticipacion().getSala().getId()))
				.orElseThrow(() -> new ResourceNotFoundException(
						"La respuesta no existe."));
		return aResponse(respuesta, sala);
	}

	@Override
	@Transactional(readOnly = true)
	public RespuestaSalaResponse mostrarPorParticipacionYPregunta(
			Long salaId, Long participacionId, Long preguntaId) {
		validarIdentificador(participacionId);
		validarIdentificador(preguntaId);
		Sala sala = buscarSala(salaId);
		RespuestaSala respuesta = respuestaSalaRepository
				.findByParticipacion_IdAndPregunta_Id(participacionId, preguntaId)
				.filter(r -> salaId.equals(
						r.getParticipacion().getSala().getId()))
				.orElseThrow(() -> new ResourceNotFoundException(
						"La respuesta no existe."));
		return aResponse(respuesta, sala);
	}

	@Override
	@Transactional(readOnly = true)
	public List<RespuestaSalaResponse> listarPorParticipacion(
			Long salaId, Long participacionId) {
		validarIdentificador(participacionId);
		Sala sala = buscarSala(salaId);
		participacionSalaRepository.findById(participacionId)
				.filter(p -> salaId.equals(p.getSala().getId()))
				.orElseThrow(() -> new ResourceNotFoundException(
						"La participación no existe."));
		return respuestaSalaRepository
				.findAllByParticipacion_Id(participacionId).stream()
				.map(respuesta -> aResponse(respuesta, sala))
				.toList();
	}

	private Sala buscarSala(Long salaId) {
		validarIdentificador(salaId);
		return salaRepository.findById(salaId)
				.orElseThrow(() -> new ResourceNotFoundException(
						"La sala no existe."));
	}

	private void validarIdentificador(Long id) {
		if (id == null || id <= 0) {
			throw new SolicitudInvalidaException("El identificador debe ser positivo.");
		}
	}

	private RespuestaSalaResponse aResponse(RespuestaSala respuesta, Sala sala) {
		return new RespuestaSalaResponse(
				respuesta.getId(),
				respuesta.getParticipacion().getId(),
				respuesta.getPregunta().getId(),
				revelarAcierto(sala, respuesta),
				respuesta.isOmitida(),
				respuesta.getTiempoMs());
	}

	/**
	 * El acierto se oculta mientras la pregunta siga abierta (o pausada) para
	 * que nadie pueda deducir la alternativa correcta desde su propia respuesta.
	 * Se revela cuando la sala finalizó o cuando ya se avanzó a otra pregunta.
	 * El valor {@code null} se omite del JSON.
	 */
	private Boolean revelarAcierto(Sala sala, RespuestaSala respuesta) {
		PreguntaDocente pregunta = respuesta.getPregunta();
		if (sala.getEstado() != EstadoSala.FINALIZADA
				&& sala.getPreguntaActual() != null && pregunta != null
				&& sala.getPreguntaActual().getId().equals(pregunta.getId())) {
			return null;
		}
		return respuesta.isAcierto();
	}
}
