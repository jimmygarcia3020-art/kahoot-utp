package com.adaptcode.utp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.adaptcode.utp.model.AlternativaDocente;
import com.adaptcode.utp.model.AmpliacionSala;
import com.adaptcode.utp.model.EstadoSala;
import com.adaptcode.utp.model.NivelMicroReto;
import com.adaptcode.utp.model.ParticipacionSala;
import com.adaptcode.utp.model.PreguntaDocente;
import com.adaptcode.utp.model.Quiz;
import com.adaptcode.utp.model.QuizItem;
import com.adaptcode.utp.model.RespuestaSala;
import com.adaptcode.utp.model.RolUsuario;
import com.adaptcode.utp.model.Sala;
import com.adaptcode.utp.model.Tema;
import com.adaptcode.utp.model.TipoMicroReto;
import com.adaptcode.utp.model.Usuario;
import com.adaptcode.utp.repository.AlternativaDocenteRepository;
import com.adaptcode.utp.repository.AnulacionPreguntaRepository;
import com.adaptcode.utp.repository.ParticipacionSalaRepository;
import com.adaptcode.utp.repository.QuizItemRepository;
import com.adaptcode.utp.repository.QuizRepository;
import com.adaptcode.utp.repository.RespuestaSalaRepository;
import com.adaptcode.utp.repository.SalaRepository;
import com.adaptcode.utp.repository.UsuarioRepository;

/**
 * La pausa debe detener el cronómetro de la pregunta, no solo bloquear las
 * respuestas. Al reanudar, el inicio de la pregunta se desplaza por la duración
 * de la pausa.
 */
class SalaServiceTest {

	private SalaRepository salaRepository;
	private UsuarioRepository usuarioRepository;
	private QuizItemRepository quizItemRepository;
	private AnulacionPreguntaRepository anulacionPreguntaRepository;
	private AlternativaDocenteRepository alternativaDocenteRepository;
	private ParticipacionSalaRepository participacionSalaRepository;
	private RespuestaSalaRepository respuestaSalaRepository;
	private SalaEventoService salaEventoService;
	private SalaServiceImp servicio;
	private Sala sala;

	@BeforeEach
	void configurar() {
		salaRepository = mock(SalaRepository.class);
		usuarioRepository = mock(UsuarioRepository.class);
		QuizRepository quizRepository = mock(QuizRepository.class);
		quizItemRepository = mock(QuizItemRepository.class);
		anulacionPreguntaRepository = mock(AnulacionPreguntaRepository.class);
		alternativaDocenteRepository = mock(AlternativaDocenteRepository.class);
		participacionSalaRepository = mock(ParticipacionSalaRepository.class);
		respuestaSalaRepository = mock(RespuestaSalaRepository.class);
		salaEventoService = new SalaEventoService(60_000L, 5, 5, 60_000L);
		servicio = new SalaServiceImp(salaRepository, usuarioRepository,
				quizRepository, quizItemRepository, anulacionPreguntaRepository,
				alternativaDocenteRepository, participacionSalaRepository,
				respuestaSalaRepository, new RankingCache(), salaEventoService);

		Usuario docente = mock(Usuario.class);
		when(docente.getId()).thenReturn(2L);
		when(docente.getRol()).thenReturn(RolUsuario.DOCENTE_UTP);
		when(docente.isActivo()).thenReturn(true);
		when(usuarioRepository.findById(2L)).thenReturn(Optional.of(docente));

		Quiz quiz = mock(Quiz.class);
		when(quiz.getId()).thenReturn(200L);

		sala = new Sala(docente, quiz, "482913", 30);
		ReflectionTestUtils.setField(sala, "id", 5L);
		sala.setEstado(EstadoSala.EN_CURSO);
		sala.setAmpliacion(AmpliacionSala.ESTANDAR);

		when(salaRepository.findById(5L)).thenReturn(Optional.of(sala));
		when(salaRepository.save(any()))
				.thenAnswer(invocacion -> invocacion.getArgument(0));
	}

	@AfterEach
	void cerrar() {
		salaEventoService.detenerLatido();
	}

	@Test
	void pausarDetieneElCronometroYReanudarLoDesplaza() {
		Instant inicio = Instant.now().minusSeconds(10);
		sala.setPreguntaIniciadaEn(inicio);

		var pausada = servicio.pausarSala(2L, 5L);

		assertEquals(EstadoSala.PAUSADA, pausada.estado());
		assertNotNull(pausada.pausadaEn());
		assertEquals(inicio, pausada.preguntaIniciadaEn());

		// Simula que la sala estuvo pausada cinco segundos.
		sala.setPausadaEn(Instant.now().minusSeconds(5));

		var reanudada = servicio.reanudarSala(2L, 5L);

		assertEquals(EstadoSala.EN_CURSO, reanudada.estado());
		assertNull(reanudada.pausadaEn());
		long desplazamiento = Duration
				.between(inicio, reanudada.preguntaIniciadaEn()).toMillis();
		assertTrue(desplazamiento >= 4_500L && desplazamiento <= 6_500L,
				"El inicio debe desplazarse por la pausa: " + desplazamiento);
	}

	@Test
	void estadoPreguntaPausadaNoConsumeElPlazo() {
		sala.setEstado(EstadoSala.PAUSADA);
		sala.setPreguntaIniciadaEn(Instant.now().minusSeconds(10));
		sala.setPausadaEn(Instant.now());

		var estado = servicio.estadoPregunta(5L);

		assertEquals(EstadoSala.PAUSADA, estado.estado());
		assertEquals(30, estado.segundosLimitePregunta());
		assertTrue(estado.milisegundosRestantes() > 19_000L,
				"Con 30 s de límite y 10 s transcurridos deben quedar ~20 s: "
						+ estado.milisegundosRestantes());
		assertTrue(estado.milisegundosRestantes() <= 20_000L);
	}

	@Test
	void preguntaActualDevuelveAlternativasSinLaCorrectaYElEnvioDelEstudiante() {
		PreguntaDocente pregunta = mock(PreguntaDocente.class);
		when(pregunta.getId()).thenReturn(77L);
		when(pregunta.getEnunciado()).thenReturn("¿Cuál es la salida?");
		when(pregunta.getTipo()).thenReturn(TipoMicroReto.SELECCION_MULTIPLE);
		when(pregunta.getNivel()).thenReturn(NivelMicroReto.BASICO);
		Tema tema = mock(Tema.class);
		when(tema.getNombre()).thenReturn("Bucles");
		when(pregunta.getTema()).thenReturn(tema);

		sala.setPreguntaActual(pregunta);

		QuizItem primero = mock(QuizItem.class);
		when(primero.getPregunta()).thenReturn(pregunta);
		when(primero.getOrden()).thenReturn(1);
		QuizItem segundo = mock(QuizItem.class);
		when(segundo.getPregunta()).thenReturn(mock(PreguntaDocente.class));
		when(segundo.getOrden()).thenReturn(2);
		when(quizItemRepository.findAllByQuiz_IdOrderByOrdenAsc(200L))
				.thenReturn(List.of(primero, segundo));
		when(anulacionPreguntaRepository.findAllBySala_Id(5L))
				.thenReturn(List.of());

		AlternativaDocente alternativa = mock(AlternativaDocente.class);
		when(alternativa.getId()).thenReturn(11L);
		when(alternativa.getTexto()).thenReturn("10");
		when(alternativa.getOrdenVisual()).thenReturn(1);
		when(alternativaDocenteRepository
				.findAllByPregunta_IdOrderByOrdenVisualAsc(77L))
				.thenReturn(List.of(alternativa));

		ParticipacionSala participacion = mock(ParticipacionSala.class);
		when(participacion.getId()).thenReturn(9L);
		when(participacionSalaRepository
				.findAllBySala_IdAndEstudiante_Id(5L, 3L))
				.thenReturn(List.of(participacion));
		RespuestaSala respuesta = mock(RespuestaSala.class);
		when(respuesta.isOmitida()).thenReturn(false);
		when(respuesta.getAlternativaElegida()).thenReturn(alternativa);
		when(respuestaSalaRepository
				.findByParticipacion_IdAndPregunta_Id(9L, 77L))
				.thenReturn(Optional.of(respuesta));

		// Se fija justo antes de la llamada: el armado de los mocks puede tardar
		// más de un segundo en máquinas lentas y la prueba mide el cronómetro.
		sala.setPreguntaIniciadaEn(Instant.now().minusSeconds(5));
		var actual = servicio.preguntaActual(5L, 3L);

		assertEquals("¿Cuál es la salida?", actual.enunciado());
		assertEquals(1, actual.numeroPregunta());
		assertEquals(2, actual.totalPreguntas());
		assertEquals(1, actual.alternativas().size());
		assertEquals(11L, actual.alternativas().get(0).id());
		assertTrue(actual.respondida());
		assertEquals(11L, actual.alternativaSeleccionadaId());
		assertTrue(actual.milisegundosRestantes() > 24_000L,
				"Con 30 s de límite y 5 s transcurridos deben quedar ~25 s: "
						+ actual.milisegundosRestantes());
	}
}
