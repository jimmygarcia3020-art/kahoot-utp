package com.adaptcode.utp.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.adaptcode.utp.dto.request.SalaRequest;
import com.adaptcode.utp.dto.response.AlternativaSalaResponse;
import com.adaptcode.utp.dto.response.EstadoPreguntaResponse;
import com.adaptcode.utp.dto.response.PreguntaSalaResponse;
import com.adaptcode.utp.dto.response.SalaResponse;
import com.adaptcode.utp.exception.ResourceNotFoundException;
import com.adaptcode.utp.exception.SolicitudInvalidaException;
import com.adaptcode.utp.model.AlternativaDocente;
import com.adaptcode.utp.model.EstadoQuiz;
import com.adaptcode.utp.model.EstadoSala;
import com.adaptcode.utp.model.ParticipacionSala;
import com.adaptcode.utp.model.Quiz;
import com.adaptcode.utp.model.QuizItem;
import com.adaptcode.utp.model.RespuestaSala;
import com.adaptcode.utp.model.RolUsuario;
import com.adaptcode.utp.model.Sala;
import com.adaptcode.utp.model.Usuario;
import com.adaptcode.utp.repository.AlternativaDocenteRepository;
import com.adaptcode.utp.repository.AnulacionPreguntaRepository;
import com.adaptcode.utp.repository.ParticipacionSalaRepository;
import com.adaptcode.utp.repository.QuizItemRepository;
import com.adaptcode.utp.repository.QuizRepository;
import com.adaptcode.utp.repository.RespuestaSalaRepository;
import com.adaptcode.utp.repository.SalaRepository;
import com.adaptcode.utp.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SalaServiceImp implements SalaService {

	private static final Set<Integer> TIEMPOS_VALIDOS = Set.of(20, 30, 60, 90, 120);

	private final SalaRepository salaRepository;
	private final UsuarioRepository usuarioRepository;
	private final QuizRepository quizRepository;
	private final QuizItemRepository quizItemRepository;
	private final AnulacionPreguntaRepository anulacionPreguntaRepository;
	private final AlternativaDocenteRepository alternativaDocenteRepository;
	private final ParticipacionSalaRepository participacionSalaRepository;
	private final RespuestaSalaRepository respuestaSalaRepository;
	private final RankingCache rankingCache;
	private final SalaEventoService salaEventoService;
	private final SecureRandom pinAleatorio = new SecureRandom();

	@Override
	@Transactional
	public SalaResponse crearSala(Long docenteId, SalaRequest request) {
		Usuario docente = validarDocente(docenteId);
		Quiz quiz = quizRepository.buscarConItemsPorIdYDocente(request.quizId(), docenteId)
				.orElseThrow(() -> new ResourceNotFoundException("El quiz no existe."));
		if (quiz.getEstado() != EstadoQuiz.LISTO) {
			throw new SolicitudInvalidaException(
					"Solo un quiz Listo para usar puede originar una sala.");
		}
		if (!TIEMPOS_VALIDOS.contains(request.tiempoPorPregunta())) {
			throw new SolicitudInvalidaException(
					"El tiempo por pregunta debe ser 20, 30, 60, 90 o 120 segundos.");
		}
		Sala sala = new Sala(docente, quiz, generarPinUnico(), request.tiempoPorPregunta());
		sala.setAmpliacion(request.ampliacion());
		sala.setQuizVersionCongeladaEn(Instant.now());
		return aResponse(salaRepository.save(sala));
	}

	@Override
	@Transactional(readOnly = true)
	public SalaResponse mostrarPorId(Long docenteId, Long id) {
		validarIdentificador(id);
		validarDocente(docenteId);
		Sala sala = salaRepository.findById(id)
				.filter(s -> docenteId.equals(s.getDocente().getId()))
				.orElseThrow(() -> new ResourceNotFoundException("La sala no existe."));
		return aResponse(sala);
	}

	@Override
	@Transactional(readOnly = true)
	public SalaResponse mostrarPorPin(String pin) {
		if (pin == null || !pin.matches("\\d{6}")) {
			throw new SolicitudInvalidaException(
					"El PIN debe tener seis dígitos numéricos.");
		}
		Sala sala = salaRepository.findByPin(pin)
				.orElseThrow(() -> new ResourceNotFoundException(
						"La sala no existe."));
		return aResponse(sala);
	}

	@Override
	@Transactional(readOnly = true)
	public List<SalaResponse> listarPorDocente(Long docenteId, EstadoSala estado) {
		validarDocente(docenteId);
		List<Sala> salas = estado == null
				? salaRepository.findAllByDocente_IdOrderByIdDesc(docenteId)
				: salaRepository.buscarPorDocenteYEstado(docenteId, estado);
		return salas.stream()
				.map(this::aResponse)
				.toList();
	}

	@Override
	@Transactional
	public SalaResponse abrirEspera(Long docenteId, Long id) {
		Sala sala = buscarSalaDeDocente(docenteId, id);
		if (sala.getEstado() != EstadoSala.BORRADOR) {
			throw new SolicitudInvalidaException(
					"Solo una sala en borrador puede abrir la espera.");
		}
		sala.setEstado(EstadoSala.SALA_ESPERA);
		return guardarYPublicar(sala);
	}

	@Override
	@Transactional
	public SalaResponse iniciarSala(Long docenteId, Long id) {
		Sala sala = buscarSalaDeDocente(docenteId, id);
		if (sala.getEstado() != EstadoSala.BORRADOR
				&& sala.getEstado() != EstadoSala.SALA_ESPERA) {
			throw new SolicitudInvalidaException(
					"Solo una sala en borrador o espera se puede iniciar.");
		}
		if (sala.getQuizOrigen().getEstado() != EstadoQuiz.LISTO) {
			throw new SolicitudInvalidaException(
					"Solo un quiz Listo para usar puede originar una sala.");
		}
		sala.setEstado(EstadoSala.EN_CURSO);
		if (sala.getQuizVersionCongeladaEn() == null) {
			sala.setQuizVersionCongeladaEn(Instant.now());
		}
		// El servidor habilita la primera pregunta: el cronómetro empieza aquí.
		if (sala.getPreguntaActual() == null) {
			habilitar(sala, primeraPregunta(sala));
		}
		return guardarYPublicar(sala);
	}

	@Override
	@Transactional
	public SalaResponse habilitarSiguientePregunta(Long docenteId, Long id) {
		Sala sala = buscarSalaDeDocente(docenteId, id);
		if (sala.getEstado() != EstadoSala.EN_CURSO) {
			throw new SolicitudInvalidaException(
					"Solo se puede avanzar la pregunta con la sala en curso.");
		}
		List<QuizItem> items = quizItemRepository
				.findAllByQuiz_IdOrderByOrdenAsc(sala.getQuizOrigen().getId());
		Set<Long> anuladas = anulacionPreguntaRepository
				.findAllBySala_Id(sala.getId()).stream()
				.map(a -> a.getPregunta().getId())
				.collect(Collectors.toSet());
		Integer ordenActual = sala.getPreguntaActual() == null ? null
				: items.stream()
						.filter(i -> i.getPregunta().getId()
								.equals(sala.getPreguntaActual().getId()))
						.map(QuizItem::getOrden)
						.findFirst()
						.orElse(null);
		QuizItem siguiente = items.stream()
				.filter(item -> !anuladas.contains(item.getPregunta().getId()))
				.filter(item -> ordenActual == null
						|| item.getOrden() > ordenActual)
				.findFirst()
				.orElseThrow(() -> new SolicitudInvalidaException(
						"No hay más preguntas disponibles en esta sala."));
		habilitar(sala, siguiente);
		return guardarYPublicar(sala);
	}

	private void habilitar(Sala sala, QuizItem item) {
		sala.setPreguntaActual(item.getPregunta());
		sala.setPreguntaIniciadaEn(Instant.now());
		sala.setPausadaEn(null);
	}

	private QuizItem primeraPregunta(Sala sala) {
		List<QuizItem> items = quizItemRepository
				.findAllByQuiz_IdOrderByOrdenAsc(sala.getQuizOrigen().getId());
		Set<Long> anuladas = anulacionPreguntaRepository
				.findAllBySala_Id(sala.getId()).stream()
				.map(a -> a.getPregunta().getId())
				.collect(Collectors.toSet());
		return items.stream()
				.filter(item -> !anuladas.contains(item.getPregunta().getId()))
				.findFirst()
				.orElseThrow(() -> new SolicitudInvalidaException(
						"El quiz no tiene preguntas disponibles."));
	}

	@Override
	@Transactional
	public SalaResponse pausarSala(Long docenteId, Long id) {
		Sala sala = buscarSalaDeDocente(docenteId, id);
		if (sala.getEstado() != EstadoSala.EN_CURSO) {
			throw new SolicitudInvalidaException(
					"Solo una sala en curso se puede pausar.");
		}
		sala.setEstado(EstadoSala.PAUSADA);
		sala.setPausadaEn(Instant.now());
		return guardarYPublicar(sala);
	}

	@Override
	@Transactional
	public SalaResponse reanudarSala(Long docenteId, Long id) {
		Sala sala = buscarSalaDeDocente(docenteId, id);
		if (sala.getEstado() != EstadoSala.PAUSADA) {
			throw new SolicitudInvalidaException(
					"Solo una sala pausada se puede reanudar.");
		}
		Instant ahora = Instant.now();
		// El cronómetro se detuvo durante la pausa: se desplaza el inicio para
		// que el tiempo pausado no consuma el plazo de respuesta.
		if (sala.getPausadaEn() != null && sala.getPreguntaIniciadaEn() != null) {
			Duration pausa = Duration.between(sala.getPausadaEn(), ahora);
			if (!pausa.isNegative()) {
				sala.setPreguntaIniciadaEn(
						sala.getPreguntaIniciadaEn().plus(pausa));
			}
		}
		sala.setPausadaEn(null);
		sala.setEstado(EstadoSala.EN_CURSO);
		return guardarYPublicar(sala);
	}

	@Override
	@Transactional
	public SalaResponse finalizarSala(Long docenteId, Long id) {
		Sala sala = buscarSalaDeDocente(docenteId, id);
		if (sala.getEstado() != EstadoSala.EN_CURSO
				&& sala.getEstado() != EstadoSala.PAUSADA) {
			throw new SolicitudInvalidaException(
					"Solo una sala en curso o pausada se puede finalizar.");
		}
		sala.setEstado(EstadoSala.FINALIZADA);
		sala.setPausadaEn(null);
		return guardarYPublicar(sala);
	}

	/**
	 * Estado del cronómetro de la pregunta actual. No incluye el enunciado ni
	 * las alternativas: solo lo necesario para que el cliente muestre el tiempo
	 * restante sin convertirse en autoridad del puntaje.
	 */
	@Override
	@Transactional(readOnly = true)
	public EstadoPreguntaResponse estadoPregunta(Long salaId) {
		validarIdentificador(salaId);
		Sala sala = salaRepository.findById(salaId)
				.orElseThrow(() -> new ResourceNotFoundException(
						"La sala no existe."));
		int limiteSegundos = sala.segundosLimitePregunta();
		return new EstadoPreguntaResponse(
				sala.getId(),
				sala.getEstado(),
				sala.getPreguntaActual() == null ? null
						: sala.getPreguntaActual().getId(),
				sala.getPreguntaIniciadaEn(),
				sala.getPausadaEn(),
				limiteSegundos,
				restanteMs(sala),
				sala.getVersion());
	}

	/**
	 * Pregunta habilitada para jugar. La vista es deliberadamente pública: no
	 * incluye la alternativa correcta ni retroalimentación. Si el estudiante ya
	 * envió su respuesta, se lo informa para que el cliente no lo deje
	 * responder dos veces.
	 */
	@Override
	@Transactional(readOnly = true)
	public PreguntaSalaResponse preguntaActual(Long salaId, Long estudianteId) {
		validarIdentificador(salaId);
		Sala sala = salaRepository.findById(salaId)
				.orElseThrow(() -> new ResourceNotFoundException(
						"La sala no existe."));
		if (sala.getEstado() != EstadoSala.EN_CURSO
				&& sala.getEstado() != EstadoSala.PAUSADA) {
			throw new ResourceNotFoundException(
					"No hay una pregunta disponible en esta sala.");
		}
		if (sala.getPreguntaActual() == null) {
			throw new ResourceNotFoundException(
					"No hay una pregunta disponible en esta sala.");
		}
		Long preguntaId = sala.getPreguntaActual().getId();
		List<AlternativaSalaResponse> alternativas = alternativaDocenteRepository
				.findAllByPregunta_IdOrderByOrdenVisualAsc(preguntaId).stream()
				.map(a -> new AlternativaSalaResponse(a.getId(), a.getTexto(),
						a.getCodigo(), a.getLenguaje(), a.getOrdenVisual()))
				.toList();

		boolean respondida = false;
		boolean omitida = false;
		Long alternativaSeleccionadaId = null;
		if (estudianteId != null) {
			Optional<ParticipacionSala> participacion = participacionSalaRepository
					.findAllBySala_IdAndEstudiante_Id(salaId, estudianteId)
					.stream()
					.findFirst();
			if (participacion.isPresent()) {
				Optional<RespuestaSala> respuesta = respuestaSalaRepository
						.findByParticipacion_IdAndPregunta_Id(
								participacion.get().getId(), preguntaId);
				if (respuesta.isPresent()) {
					respondida = true;
					omitida = respuesta.get().isOmitida();
					if (respuesta.get().getAlternativaElegida() != null) {
						alternativaSeleccionadaId = respuesta.get()
								.getAlternativaElegida().getId();
					}
				}
			}
		}

		Numeracion numeracion = numerar(sala, preguntaId);
		return new PreguntaSalaResponse(
				sala.getId(),
				sala.getEstado(),
				preguntaId,
				numeracion.numero(),
				numeracion.total(),
				sala.getPreguntaActual().getEnunciado(),
				sala.getPreguntaActual().getTipo(),
				sala.getPreguntaActual().getNivel(),
				sala.getPreguntaActual().getTema() == null ? null
						: sala.getPreguntaActual().getTema().getNombre(),
				alternativas,
				sala.getPreguntaIniciadaEn(),
				sala.getPausadaEn(),
				sala.segundosLimitePregunta(),
				restanteMs(sala),
				respondida,
				alternativaSeleccionadaId,
				omitida);
	}

	/**
	 * Posición (1-based) de la pregunta actual dentro de las preguntas
	 * disponibles del quiz, excluyendo las anuladas en esta sala. Si la
	 * pregunta vigente fue anulada después de habilitarse, se usa la posición
	 * del quiz completo para no devolver una numeración nula.
	 */
	private Numeracion numerar(Sala sala, Long preguntaId) {
		List<QuizItem> items = quizItemRepository
				.findAllByQuiz_IdOrderByOrdenAsc(sala.getQuizOrigen().getId());
		Set<Long> anuladas = anulacionPreguntaRepository
				.findAllBySala_Id(sala.getId()).stream()
				.map(a -> a.getPregunta().getId())
				.collect(Collectors.toSet());
		List<QuizItem> disponibles = items.stream()
				.filter(item -> !anuladas.contains(item.getPregunta().getId()))
				.toList();
		Integer numero = posicion(disponibles, preguntaId);
		if (numero == null) {
			numero = posicion(items, preguntaId);
		}
		return new Numeracion(numero, disponibles.size());
	}

	private Integer posicion(List<QuizItem> items, Long preguntaId) {
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).getPregunta().getId().equals(preguntaId)) {
				return i + 1;
			}
		}
		return null;
	}

	/** Tiempo restante en el servidor; durante la pausa el reloj no corre. */
	private long restanteMs(Sala sala) {
		if (sala.getPreguntaIniciadaEn() == null) {
			return 0L;
		}
		Instant referencia = sala.getEstado() == EstadoSala.PAUSADA
				&& sala.getPausadaEn() != null
						? sala.getPausadaEn()
						: Instant.now();
		long transcurrido = Math.max(0L,
				Duration.between(sala.getPreguntaIniciadaEn(), referencia)
						.toMillis());
		return Math.max(0L, sala.segundosLimitePregunta() * 1000L - transcurrido);
	}

	private record Numeracion(Integer numero, int total) {
	}

	private Sala buscarSalaDeDocente(Long docenteId, Long id) {
		validarDocente(docenteId);
		validarIdentificador(id);
		Sala sala = salaRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException(
						"La sala no existe."));
		if (!docenteId.equals(sala.getDocente().getId())) {
			throw new ResourceNotFoundException("La sala no existe.");
		}
		return sala;
	}

	private String generarPinUnico() {
		for (int intento = 0; intento < 20; intento++) {
			String pin = String.format("%06d", pinAleatorio.nextInt(1000000));
			if (!salaRepository.existsByPin(pin)) {
				return pin;
			}
		}
		throw new SolicitudInvalidaException("No se pudo generar un PIN único.");
	}

	private Usuario validarDocente(Long docenteId) {
		validarIdentificador(docenteId);
		Usuario docente = usuarioRepository.findById(docenteId)
				.orElseThrow(() -> new ResourceNotFoundException("El docente no existe."));
		if (docente.getRol() != RolUsuario.DOCENTE_UTP || !docente.isActivo()) {
			throw new SolicitudInvalidaException("El usuario no es un docente activo.");
		}
		return docente;
	}

	private void validarIdentificador(Long id) {
		if (id == null || id <= 0) {
			throw new SolicitudInvalidaException("El identificador debe ser positivo.");
		}
	}

	private SalaResponse guardarYPublicar(Sala sala) {
		SalaResponse response = aResponse(salaRepository.save(sala));
		// Cambiar de pregunta o de estado altera qué respuestas cuentan para el
		// ranking (y si se revela el acierto): no se sirve una entrada vieja.
		rankingCache.invalidar(sala.getId());
		salaEventoService.publicarTrasCommit(sala.getId(),
				SalaEventoService.EVENTO_SALA, response);
		return response;
	}

	private SalaResponse aResponse(Sala sala) {
		return new SalaResponse(
				sala.getId(),
				sala.getDocente().getId(),
				sala.getQuizOrigen().getId(),
				sala.getPin(),
				sala.getEstado(),
				sala.getTiempoPorPregunta(),
				sala.getAmpliacion(),
				sala.getQuizVersionCongeladaEn(),
				sala.getPreguntaActual() == null ? null
						: sala.getPreguntaActual().getId(),
				sala.getPreguntaIniciadaEn(),
				sala.getPausadaEn(),
				sala.segundosLimitePregunta(),
				sala.getVersion());
	}
}
