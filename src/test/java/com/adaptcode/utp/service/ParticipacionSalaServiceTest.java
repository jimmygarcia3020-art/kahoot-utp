package com.adaptcode.utp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.adaptcode.utp.dto.request.ParticipacionSalaRequest;
import com.adaptcode.utp.exception.AccesoDenegadoException;
import com.adaptcode.utp.model.EstadoSala;
import com.adaptcode.utp.model.RolUsuario;
import com.adaptcode.utp.model.Sala;
import com.adaptcode.utp.model.Usuario;
import com.adaptcode.utp.repository.ParticipacionSalaRepository;
import com.adaptcode.utp.repository.SalaRepository;
import com.adaptcode.utp.repository.UsuarioRepository;

/**
 * El PIN es la única credencial que autoriza el ingreso a una sala. Un PIN
 * incorrecto debe rechazarse con 403 antes de tocar al estudiante o guardar la
 * participación, para no filtrar información ni dejar filas huérfanas.
 */
class ParticipacionSalaServiceTest {

	private ParticipacionSalaRepository participacionSalaRepository;
	private SalaRepository salaRepository;
	private UsuarioRepository usuarioRepository;
	private SalaEventoService salaEventoService;
	private ParticipacionSalaServiceImp servicio;
	private Sala sala;

	@BeforeEach
	void configurar() {
		participacionSalaRepository = mock(ParticipacionSalaRepository.class);
		salaRepository = mock(SalaRepository.class);
		usuarioRepository = mock(UsuarioRepository.class);
		salaEventoService = new SalaEventoService(60_000L, 5, 5, 60_000L);
		servicio = new ParticipacionSalaServiceImp(participacionSalaRepository,
				salaRepository, usuarioRepository, new RankingCache(),
				salaEventoService);

		sala = mock(Sala.class);
		when(sala.getId()).thenReturn(5L);
		when(sala.getPin()).thenReturn("482913");
		when(sala.getEstado()).thenReturn(EstadoSala.SALA_ESPERA);
		when(salaRepository.findById(5L)).thenReturn(Optional.of(sala));

		Usuario estudiante = mock(Usuario.class);
		when(estudiante.getId()).thenReturn(9L);
		when(estudiante.getRol()).thenReturn(RolUsuario.ESTUDIANTE_UTP);
		when(estudiante.isActivo()).thenReturn(true);
		when(usuarioRepository.findById(9L)).thenReturn(Optional.of(estudiante));
	}

	@AfterEach
	void cerrar() {
		salaEventoService.detenerLatido();
	}

	@Test
	void pinIncorrecto_rechazaConAccesoDenegadoSinRegistrar() {
		assertThrows(AccesoDenegadoException.class, () -> servicio.unirse(5L,
				new ParticipacionSalaRequest(9L, "111111")));

		verifyNoInteractions(usuarioRepository);
		verify(participacionSalaRepository, never()).save(any());
	}

	@Test
	void pinCorrecto_registraLaParticipacion() {
		when(participacionSalaRepository
				.existsBySala_IdAndEstudiante_Id(5L, 9L)).thenReturn(false);
		when(participacionSalaRepository.save(any()))
				.thenAnswer(invocacion -> invocacion.getArgument(0));

		var respuesta = servicio.unirse(5L,
				new ParticipacionSalaRequest(9L, "482913"));

		assertEquals(5L, respuesta.salaId());
		assertEquals(9L, respuesta.estudianteId());
		assertFalse(respuesta.expulsado());
		verify(participacionSalaRepository).save(any());
	}
}
