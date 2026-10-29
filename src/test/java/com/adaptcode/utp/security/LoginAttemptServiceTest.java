package com.adaptcode.utp.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LoginAttemptServiceTest {

	@Test
	void noBloqueaAntesDelUmbral() {
		LoginAttemptService servicio = new LoginAttemptService(3, 15);
		servicio.registrarFallo("ana@correo.com");
		servicio.registrarFallo("ANA@correo.com");
		assertFalse(servicio.estaBloqueado("ana@correo.com"));
	}

	@Test
	void bloqueaAlAlcanzarElUmbral() {
		LoginAttemptService servicio = new LoginAttemptService(3, 15);
		servicio.registrarFallo("ana@correo.com");
		servicio.registrarFallo("ana@correo.com");
		servicio.registrarFallo("ana@correo.com");

		assertTrue(servicio.estaBloqueado("ana@correo.com"));
		assertTrue(servicio.estaBloqueado("Ana@Correo.com"),
				"El correo se normaliza sin distinguir mayúsculas.");
		assertTrue(servicio.segundosRestantes("ana@correo.com") > 0);
	}

	@Test
	void elExitoReiniciaElContador() {
		LoginAttemptService servicio = new LoginAttemptService(2, 15);
		servicio.registrarFallo("ana@correo.com");
		servicio.registrarExito("ana@correo.com");
		servicio.registrarFallo("ana@correo.com");
		assertFalse(servicio.estaBloqueado("ana@correo.com"));
	}

	@Test
	void correosDistintosNoSeAfectan() {
		LoginAttemptService servicio = new LoginAttemptService(1, 15);
		servicio.registrarFallo("ana@correo.com");
		assertTrue(servicio.estaBloqueado("ana@correo.com"));
		assertFalse(servicio.estaBloqueado("luis@correo.com"));
	}
}
