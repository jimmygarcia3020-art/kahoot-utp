package com.adaptcode.utp.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.adaptcode.utp.exception.SolicitudInvalidaException;

class PoliticaContrasenaTest {

	private final PoliticaContrasena politica = new PoliticaContrasena();

	@Test
	void aceptaUnaContrasenaFuerte() {
		assertDoesNotThrow(() -> politica.validar("Nube#42Verde", "ana@correo.com",
				"Ana", "Torres"));
	}

	@Test
	void rechazaLongitudCorta() {
		SolicitudInvalidaException ex = assertThrows(
				SolicitudInvalidaException.class,
				() -> politica.validar("Ab#1Corta", null));
		assertTrue(ex.getMessage().contains("al menos 12 caracteres"));
	}

	@Test
	void exigeMayusculaMinusculaNumeroYSimbolo() {
		SolicitudInvalidaException ex = assertThrows(
				SolicitudInvalidaException.class,
				() -> politica.validar("solominusculas1#", null));
		assertTrue(ex.getMessage().contains("mayúscula"));

		ex = assertThrows(SolicitudInvalidaException.class,
				() -> politica.validar("SOLOMAYUSCULAS#", null));
		assertTrue(ex.getMessage().contains("minúscula"));

		ex = assertThrows(SolicitudInvalidaException.class,
				() -> politica.validar("SinNumeros#Abc", null));
		assertTrue(ex.getMessage().contains("número"));

		ex = assertThrows(SolicitudInvalidaException.class,
				() -> politica.validar("SinSimbolo123A", null));
		assertTrue(ex.getMessage().contains("símbolo"));
	}

	@Test
	void rechazaEspacios() {
		SolicitudInvalidaException ex = assertThrows(
				SolicitudInvalidaException.class,
				() -> politica.validar("Con Espacio#1A", null));
		assertTrue(ex.getMessage().contains("sin espacios"));
	}

	@Test
	void rechazaContrasenasComunes() {
		SolicitudInvalidaException ex = assertThrows(
				SolicitudInvalidaException.class,
				() -> politica.validar("Password#1234", null));
		assertTrue(ex.getMessage().contains("contraseña común"));
	}

	@Test
	void rechazaDatosPersonalesYCorreo() {
		SolicitudInvalidaException ex = assertThrows(
				SolicitudInvalidaException.class,
				() -> politica.validar("AnaTorres#2026", "anatorres@utp.edu.pe"));
		assertTrue(ex.getMessage().contains("correo")
				|| ex.getMessage().contains("datos personales"));

		ex = assertThrows(SolicitudInvalidaException.class,
				() -> politica.validar("Torres#2026Abc", null, "Ana", "Torres"));
		assertTrue(ex.getMessage().contains("datos personales"));
	}

	@Test
	void rechazaContrasenaVacia() {
		assertThrows(SolicitudInvalidaException.class,
				() -> politica.validar("   ", null));
		assertThrows(SolicitudInvalidaException.class,
				() -> politica.validar(null, null));
	}
}
