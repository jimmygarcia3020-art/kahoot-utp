package com.adaptcode.utp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.adaptcode.utp.dto.request.RespuestaSalaRequest;
import com.adaptcode.utp.exception.SolicitudInvalidaException;
import com.adaptcode.utp.model.EstadoSala;
import com.adaptcode.utp.model.ParticipacionSala;
import com.adaptcode.utp.model.PreguntaDocente;
import com.adaptcode.utp.model.Quiz;
import com.adaptcode.utp.model.RespuestaSala;
import com.adaptcode.utp.model.Sala;
import com.adaptcode.utp.repository.AlternativaDocenteRepository;
import com.adaptcode.utp.repository.AnulacionPreguntaRepository;
import com.adaptcode.utp.repository.ParticipacionSalaRepository;
import com.adaptcode.utp.repository.PreguntaDocenteRepository;
import com.adaptcode.utp.repository.QuizItemRepository;
import com.adaptcode.utp.repository.RespuestaSalaRepository;
import com.adaptcode.utp.repository.SalaRepository;

/**
 * El servidor es la autoridad del tiempo: una respuesta que llega después del
 * plazo (más el margen de red) se rechaza en lugar de recortar el tiempo. El
 * tiempo enviado por el cliente se ignora.
 */
class RespuestaSalaServiceTest {

	private RespuestaSalaRepository respuestaSalaRepository;
	private ParticipacionSalaRepository participacionSalaRepository;
	private SalaRepository salaRepository;
	private PreguntaDocenteRepository preguntaDocenteRepository;
	private QuizItemRepository quizItemRepository;
	private AnulacionPreguntaRepository anulacionPreguntaRepository;
	private SalaEventoService salaEventoService;
	private RespuestaSalaServiceImp servicio;
	private Sala sala;
	private PreguntaDocente pregunta;
	private ParticipacionSala participacion;

	@BeforeEach
	void configurar() {
		respuestaSalaRepository = mock(RespuestaSalaRepository.class);
		participacionSalaRepository = mock(ParticipacionSalaRepository.class);
		salaRepository = mock(SalaRepository.class);
		preguntaDocenteRepository = mock(PreguntaDocenteRepository.class);
		quizItemRepository = mock(QuizItemRepository.class);
		anulacionPreguntaRepository = mock(AnulacionPreguntaRepository.class);
		salaEventoService = new SalaEventoService(60_000L, 5, 5, 60_000L);
		servicio = new RespuestaSalaServiceImp(respuestaSalaRepository,
				participacionSalaRepository, salaRepository, quizItemRepository,
				preguntaDocenteRepository, anulacionPreguntaRepository,
				mock(AlternativaDocenteRepository.class), new RankingCache(),
				salaEventoService);
		ReflectionTestUtils.setField(servicio, "graciaMs", 2_000L);

		sala = mock(Sala.class);
		when(sala.getId()).thenReturn(5L);
		when(sala.getEstado()).thenReturn(EstadoSala.EN_CURSO);
		when(sala.segundosLimitePregunta()).thenReturn(30);
		when(salaRepository.findById(5L)).thenReturn(Optional.of(sala));

		Quiz quiz = mock(Quiz.class);
		when(quiz.getId()).thenReturn(200L);
		when(sala.getQuizOrigen()).thenReturn(quiz);

		pregunta = mock(PreguntaDocente.class);
		when(pregunta.getId()).thenReturn(50L);
		when(pregunta.isActiva()).thenReturn(true);
		when(sala.getPreguntaActual()).thenReturn(pregunta);
		when(preguntaDocenteRepository.findById(50L))
				.thenReturn(Optional.of(pregunta));

		participacion = mock(ParticipacionSala.class);
		when(participacion.getId()).thenReturn(70L);
		when(participacion.getSala()).thenReturn(sala);
		when(participacion.isExpulsado()).thenReturn(false);
		when(participacionSalaRepository.findById(70L))
				.thenReturn(Optional.of(participacion));

		when(quizItemRepository.existsByQuiz_IdAndPregunta_Id(200L, 50L))
				.thenReturn(true);
		when(anulacionPreguntaRepository.existsBySala_IdAndPregunta_Id(5L, 50L))
				.thenReturn(false);
		when(respuestaSalaRepository.existsByParticipacion_IdAndPregunta_Id(70L, 50L))
				.thenReturn(false);
		when(respuestaSalaRepository.save(any()))
				.thenAnswer(invocacion -> invocacion.getArgument(0));
	}

	@AfterEach
	void cerrar() {
		salaEventoService.detenerLatido();
	}

	@Test
	void respuestaFueraDePlazo_seRechazaSinGuardar() {
		when(sala.getPreguntaIniciadaEn()).thenReturn(Instant.now().minusSeconds(60));

		assertThrows(SolicitudInvalidaException.class, () -> servicio.registrar(5L,
				new RespuestaSalaRequest(70L, 50L, null, false, true, 1_000L)));

		verify(respuestaSalaRepository, never()).save(any());
	}

	@Test
	void respuestaDentroDelPlazo_usaElTiempoDelServidor() {
		when(sala.getPreguntaIniciadaEn()).thenReturn(Instant.now().minusSeconds(5));

		var respuesta = servicio.registrar(5L,
				// El cliente declara un tiempo imposiblemente bajo: se ignora.
				new RespuestaSalaRequest(70L, 50L, null, false, true, 1L));

		verify(respuestaSalaRepository).save(any(RespuestaSala.class));
		assertTrue(respuesta.tiempoMs() >= 4_000L,
				"El tiempo debe medirse en el servidor: " + respuesta.tiempoMs());
		assertTrue(respuesta.tiempoMs() <= 6_500L,
				"El tiempo debe medirse en el servidor: " + respuesta.tiempoMs());
		assertTrue(respuesta.omitida());
	}
}
