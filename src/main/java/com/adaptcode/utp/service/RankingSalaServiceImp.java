package com.adaptcode.utp.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.adaptcode.utp.dto.response.RankingSalaResponse;
import com.adaptcode.utp.exception.ResourceNotFoundException;
import com.adaptcode.utp.exception.SolicitudInvalidaException;
import com.adaptcode.utp.model.EstadoSala;
import com.adaptcode.utp.model.ParticipacionSala;
import com.adaptcode.utp.model.Sala;
import com.adaptcode.utp.repository.ParticipacionSalaRepository;
import com.adaptcode.utp.repository.RespuestaSalaRepository;
import com.adaptcode.utp.repository.RespuestaSalaRepository.PuntajeProyeccion;
import com.adaptcode.utp.repository.SalaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RankingSalaServiceImp implements RankingSalaService {

	private final SalaRepository salaRepository;
	private final ParticipacionSalaRepository participacionSalaRepository;
	private final RespuestaSalaRepository respuestaSalaRepository;
	private final RankingCache rankingCache;

	@Override
	@Transactional(readOnly = true)
	public List<RankingSalaResponse> mostrarRanking(Long salaId) {
		if (salaId == null || salaId <= 0) {
			throw new SolicitudInvalidaException("El identificador debe ser positivo.");
		}
		Sala sala = salaRepository.findById(salaId)
				.orElseThrow(() -> new ResourceNotFoundException(
						"La sala no existe."));
		// Mientras la ronda está abierta, sus aciertos no cuentan: de lo
		// contrario el ranking revelaría qué alternativa era la correcta.
		Long excluirPreguntaId = sala.getEstado() == EstadoSala.FINALIZADA
				|| sala.getPreguntaActual() == null
						? null
						: sala.getPreguntaActual().getId();
		return rankingCache.obtener(salaId,
				() -> calcularRanking(salaId, excluirPreguntaId));
	}

	private List<RankingSalaResponse> calcularRanking(Long salaId,
			Long excluirPreguntaId) {
		// Defensa en profundidad: el repositorio ya excluye expulsados, pero una
		// expulsión concurrente no debe aparecer en el ranking.
		List<ParticipacionSala> participaciones = participacionSalaRepository
				.findAllBySala_Id(salaId).stream()
				.filter(participacion -> !participacion.isExpulsado())
				.toList();
		Map<Long, PuntajeProyeccion> puntajes = respuestaSalaRepository
				.calcularPuntajes(salaId, excluirPreguntaId).stream()
				.collect(Collectors.toMap(PuntajeProyeccion::getParticipacionId,
						Function.identity(), (a, b) -> a));
		return ordenarRanking(calcularPuntajes(participaciones, puntajes));
	}

	private List<Puntaje> calcularPuntajes(
			List<ParticipacionSala> participaciones,
			Map<Long, PuntajeProyeccion> puntajes) {
		List<Puntaje> resultado = new ArrayList<>();
		for (ParticipacionSala participacion : participaciones) {
			PuntajeProyeccion proyeccion = puntajes.get(participacion.getId());
			int puntos = proyeccion == null || proyeccion.getPuntos() == null
					? 0 : proyeccion.getPuntos().intValue();
			long tiempo = proyeccion == null || proyeccion.getTiempoMs() == null
					? 0L : proyeccion.getTiempoMs();
			int respondidas = proyeccion == null
					|| proyeccion.getRespondidas() == null
							? 0 : proyeccion.getRespondidas().intValue();
			resultado.add(new Puntaje(participacion, puntos, respondidas, tiempo));
		}
		// Puntos primero; a igualdad, quien respondió (aunque fallara) supera a
		// quien dejó la pregunta en blanco; el tiempo solo desempata entre
		// respuestas equivalentes.
		resultado.sort(Comparator.comparingInt(Puntaje::puntos).reversed()
				.thenComparing(Comparator.comparingInt(Puntaje::respondidas)
						.reversed())
				.thenComparingLong(Puntaje::tiempo));
		return resultado;
	}

	private List<RankingSalaResponse> ordenarRanking(List<Puntaje> puntajes) {
		List<RankingSalaResponse> ranking = new ArrayList<>();
		int posicion = 0;
		int puntosPrevios = -1;
		int respondidasPrevias = -1;
		long tiempoPrevio = -1L;
		for (int i = 0; i < puntajes.size(); i++) {
			Puntaje actual = puntajes.get(i);
			if (actual.puntos() != puntosPrevios
					|| actual.respondidas() != respondidasPrevias
					|| actual.tiempo() != tiempoPrevio) {
				posicion = i + 1;
				puntosPrevios = actual.puntos();
				respondidasPrevias = actual.respondidas();
				tiempoPrevio = actual.tiempo();
			}
			String apellidos = actual.participacion().getEstudiante().getApellidos();
			String inicial = apellidos != null && !apellidos.isEmpty()
					? apellidos.charAt(0) + "." : "";
			ranking.add(new RankingSalaResponse(
					posicion,
					actual.participacion().getId(),
					actual.participacion().getEstudiante().getId(),
					actual.participacion().getEstudiante().getNombres(),
					inicial,
					actual.puntos(),
					actual.tiempo()));
		}
		return ranking;
	}

	private record Puntaje(ParticipacionSala participacion, int puntos,
			int respondidas, long tiempo) {
	}
}
