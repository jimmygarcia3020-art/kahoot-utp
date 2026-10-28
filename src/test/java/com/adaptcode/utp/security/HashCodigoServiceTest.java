package com.adaptcode.utp.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HashCodigoServiceTest {

	private final HashCodigoService servicio = new HashCodigoService(
			"pepper-de-prueba-con-mas-de-16-caracteres");

	@Test
	void hash_esDeterministicoYHexadecimalDe64Caracteres() {
		String primero = servicio.hashear("123456");
		String segundo = servicio.hashear("123456");

		assertEquals(primero, segundo);
		assertTrue(primero.matches("[0-9a-f]{64}"), primero);
	}

	@Test
	void coincide_aceptaHashCorrectoYRechazaOtroCodigo() {
		String almacenado = servicio.hashear("654321");

		assertTrue(servicio.coincide(almacenado, "654321"));
		assertFalse(servicio.coincide(almacenado, "000000"));
		assertFalse(servicio.coincide(almacenado, null));
	}

	@Test
	void coincide_reconoceFilasLegacyEnClaroParaMigrarlas() {
		assertTrue(servicio.esLegacy("123456"));
		assertTrue(servicio.coincide("123456", "123456"));
		assertFalse(servicio.coincide("123456", "654321"));
		assertFalse(servicio.esLegacy(servicio.hashear("123456")));
	}

	@Test
	void pepperDebilEsRechazadoAlConstruir() {
		org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
				() -> new HashCodigoService("corto"));
	}
}
