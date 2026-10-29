package com.adaptcode.utp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.adaptcode.utp.model.EstadoSala;
import com.adaptcode.utp.model.ParticipacionSala;
import com.adaptcode.utp.model.PreguntaDocente;
import com.adaptcode.utp.model.Sala;
import com.adaptcode.utp.model.Usuario;
import com.adaptcode.utp.repository.ParticipacionSalaRepository;
import com.adaptcode.utp.repository.RespuestaSalaRepository;
import com.adaptcode.utp.repository.RespuestaSalaRepository.PuntajeProyeccion;
import com.adaptcode.utp.repository.SalaRepository;

class RankingSalaServiceTest {

	private SalaRepository salaRepository;
	private ParticipacionSalaRepository participacionSalaRepository;
	private RespuestaSalaRepository respuestaSalaRepository;
	private RankingSalaServiceImp rankingSalaService;

	@BeforeEach
	void configurar() {
		salaRepository = mock(SalaRepository.class);
		participacionSalaRepository = mock(ParticipacionSalaRepository.class);
		respuestaSalaRepository = mock(RespuestaSalaRepository.class);
		rankingSalaService = new RankingSalaServiceImp(salaRepository,
				participacionSalaRepository, respuestaSalaRepository,
				new RankingCache());
	}

	@Test
	void ranking_ordenaPorPuntosDesempataPorTiempoConSalto() {
		ParticipacionSala primero = participacion(11L, 21L, "Ana", "Paredes");
		ParticipacionSala empatado = participacion(12L, 22L, "Luis", "Quispe");
		ParticipacionSala tercero = participacion(13L, 23L, "Rosa", "Díaz");
		Sala sala = salaFinalizada();
		when(salaRepository.findById(5L)).thenReturn(Optional.of(sala));
		when(participacionSalaRepository.findAllBySala_Id(5L))
				.thenReturn(List.of(primero, empatado, tercero));
		var puntajes = List.of(
				puntaje(11L, 2L, 3L, 9000L),
				puntaje(12L, 2L, 3L, 9000L),
				puntaje(13L, 1L, 2L, 4000L));
		when(respuestaSalaRepository.calcularPuntajes(5L, null))
				.thenReturn(puntajes);

		var ranking = rankingSalaService.mostrarRanking(5L);

		assertEquals(List.of(1, 1, 3),
				ranking.stream().map(r -> r.posicion()).toList());
		assertEquals(21L, ranking.get(0).estudianteId());
		assertEquals("P.", ranking.get(0).apellidoInicial());
		assertEquals(2, ranking.get(0).puntos());
		assertEquals(1, ranking.get(2).puntos());
		assertEquals(4000L, ranking.get(2).tiempoMs());
	}

	@Test
	void ranking_incluyeParticipacionesSinRespuestasConCeroPuntos() {
		ParticipacionSala sinRespuestas = participacion(14L, 24L, "Eva", "Ramos");
		Sala sala = salaFinalizada();
		when(salaRepository.findById(6L)).thenReturn(Optional.of(sala));
		when(participacionSalaRepository.findAllBySala_Id(6L))
				.thenReturn(List.of(sinRespuestas));
		when(respuestaSalaRepository.calcularPuntajes(6L, null))
				.thenReturn(List.of());

		var ranking = rankingSalaService.mostrarRanking(6L);

		assertEquals(1, ranking.size());
		assertEquals(0, ranking.get(0).puntos());
		assertEquals(0L, ranking.get(0).tiempoMs());
		assertEquals(1, ranking.get(0).posicion());
	}

	@Test
	void responderMal_superaAQuienNoRespondio() {
		ParticipacionSala respondio = participacion(15L, 25L, "Mario", "Soto");
		ParticipacionSala noRespondio = participacion(16L, 26L, "Nora", "Vega");
		Sala sala = salaFinalizada();
		when(salaRepository.findById(7L)).thenReturn(Optional.of(sala));
		when(participacionSalaRepository.findAllBySala_Id(7L))
				.thenReturn(List.of(noRespondio, respondio));
		List<PuntajeProyeccion> puntajes = List.of(
				puntaje(15L, 0L, 1L, 1500L),
				puntaje(16L, 0L, 0L, 0L));
		when(respuestaSalaRepository.calcularPuntajes(7L, null))
				.thenReturn(puntajes);

		var ranking = rankingSalaService.mostrarRanking(7L);

		assertEquals(15L, ranking.get(0).participacionId());
		assertEquals(16L, ranking.get(1).participacionId());
		assertEquals(List.of(1, 2),
				ranking.stream().map(r -> r.posicion()).toList());
	}

	@Test
	void ranking_excluyeParticipacionesExpulsadas() {
		ParticipacionSala activa = participacion(17L, 27L, "Iván", "Rojas");
		ParticipacionSala expulsada = participacion(18L, 28L, "Elena", "Luna");
		when(expulsada.isExpulsado()).thenReturn(true);
		Sala sala = salaFinalizada();
		when(salaRepository.findById(8L)).thenReturn(Optional.of(sala));
		when(participacionSalaRepository.findAllBySala_Id(8L))
				.thenReturn(List.of(activa, expulsada));
		List<PuntajeProyeccion> puntajes = List.of(
				puntaje(17L, 1L, 1L, 2000L),
				puntaje(18L, 5L, 5L, 1000L));
		when(respuestaSalaRepository.calcularPuntajes(8L, null))
				.thenReturn(puntajes);

		var ranking = rankingSalaService.mostrarRanking(8L);

		assertEquals(1, ranking.size());
		assertEquals(27L, ranking.get(0).estudianteId());
	}

	@Test
	void ranking_excluyeLaPreguntaAbiertaHastaQueSeCierreLaRonda() {
		ParticipacionSala participante = participacion(19L, 29L, "Julia", "Mesa");
		Sala sala = sala(EstadoSala.EN_CURSO, 50L);
		when(salaRepository.findById(9L)).thenReturn(Optional.of(sala));
		when(participacionSalaRepository.findAllBySala_Id(9L))
				.thenReturn(List.of(participante));
		PuntajeProyeccion proyeccion = puntaje(19L, 0L, 0L, 0L);
		when(respuestaSalaRepository.calcularPuntajes(9L, 50L))
				.thenReturn(List.of(proyeccion));

		var ranking = rankingSalaService.mostrarRanking(9L);

		assertEquals(0, ranking.get(0).puntos());
	}

	private Sala salaFinalizada() {
		return sala(EstadoSala.FINALIZADA, 50L);
	}

	private Sala sala(EstadoSala estado, Long preguntaActualId) {
		Sala sala = mock(Sala.class);
		when(sala.getEstado()).thenReturn(estado);
		if (preguntaActualId == null) {
			when(sala.getPreguntaActual()).thenReturn(null);
		} else {
			PreguntaDocente pregunta = mock(PreguntaDocente.class);
			when(pregunta.getId()).thenReturn(preguntaActualId);
			when(sala.getPreguntaActual()).thenReturn(pregunta);
		}
		return sala;
	}

	private ParticipacionSala participacion(
			Long id, Long estudianteId, String nombres, String apellidos) {
		Usuario estudiante = mock(Usuario.class);
		when(estudiante.getId()).thenReturn(estudianteId);
		when(estudiante.getNombres()).thenReturn(nombres);
		when(estudiante.getApellidos()).thenReturn(apellidos);
		Sala sala = mock(Sala.class);
		when(sala.getId()).thenReturn(5L);
		ParticipacionSala participacion = mock(ParticipacionSala.class);
		when(participacion.getId()).thenReturn(id);
		when(participacion.getSala()).thenReturn(sala);
		when(participacion.getEstudiante()).thenReturn(estudiante);
		when(participacion.isExpulsado()).thenReturn(false);
		return participacion;
	}

	private PuntajeProyeccion puntaje(Long participacionId, Long puntos,
			Long respondidas, Long tiempoMs) {
		PuntajeProyeccion proyeccion = mock(PuntajeProyeccion.class);
		when(proyeccion.getParticipacionId()).thenReturn(participacionId);
		when(proyeccion.getPuntos()).thenReturn(puntos);
		when(proyeccion.getRespondidas()).thenReturn(respondidas);
		when(proyeccion.getTiempoMs()).thenReturn(tiempoMs);
		return proyeccion;
	}
}
