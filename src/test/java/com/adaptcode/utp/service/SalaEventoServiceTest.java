package com.adaptcode.utp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.adaptcode.utp.dto.response.SalaEventoTicketResponse;
import com.adaptcode.utp.exception.AutenticacionException;

/**
 * El ticket SSE debe ser de un solo uso y estar atado a la sala. Nunca se usa
 * el JWT como parámetro de URL.
 */
class SalaEventoServiceTest {

	private SalaEventoService servicio;

	@BeforeEach
	void preparar() {
		servicio = new SalaEventoService(1_800_000L, 2, 2, 60_000L);
	}

	@AfterEach
	void cerrar() {
		servicio.detenerLatido();
	}

	@Test
	void elTicketSeCanjeaUnaSolaVez() {
		SalaEventoTicketResponse ticket = servicio.crearTicket(7L, 42L);
		assertNotNull(ticket.ticket());
		assertEquals(7L, servicio.canjearTicket(ticket.ticket(), 7L).salaId());

		assertThrows(AutenticacionException.class,
				() -> servicio.canjearTicket(ticket.ticket(), 7L));
	}

	@Test
	void elTicketNoSirveParaOtraSala() {
		SalaEventoTicketResponse ticket = servicio.crearTicket(7L, 42L);
		assertThrows(AutenticacionException.class,
				() -> servicio.canjearTicket(ticket.ticket(), 8L));
	}

	@Test
	void unTicketInexistenteEsRechazado() {
		assertThrows(AutenticacionException.class,
				() -> servicio.canjearTicket("inventado", 7L));
	}

	@Test
	void elLimiteDeConexionesPorSalaSeRespeta() {
		SseEmitter primero = servicio.suscribir(7L);
		SseEmitter segundo = servicio.suscribir(7L);
		assertNotNull(primero);
		assertNotNull(segundo);
		assertEquals(2, servicio.conexionesActivas(7L));

		assertThrows(com.adaptcode.utp.exception.CuentaBloqueadaException.class,
				() -> servicio.suscribir(7L));
	}

	@Test
	void elLimiteDeTicketsPorUsuarioSeRespeta() {
		servicio.crearTicket(7L, 42L);
		servicio.crearTicket(8L, 42L);

		assertThrows(com.adaptcode.utp.exception.CuentaBloqueadaException.class,
				() -> servicio.crearTicket(9L, 42L));
	}

	@Test
	void publicarSinSuscriptoresNoFalla() {
		servicio.publicar(99L, SalaEventoService.EVENTO_SALA,
				Map.of("salaId", 99L));
		assertEquals(0, servicio.conexionesActivas(99L));
	}
}
