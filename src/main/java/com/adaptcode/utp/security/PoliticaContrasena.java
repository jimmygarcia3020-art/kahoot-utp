package com.adaptcode.utp.security;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.adaptcode.utp.exception.SolicitudInvalidaException;

/**
 * Política central de contraseñas. Exige longitud mínima, mayúscula,
 * minúscula, número, símbolo y prohíbe espacios, datos personales y claves
 * comunes. Devuelve un mensaje con todos los requisitos incumplidos.
 */
@Component
public class PoliticaContrasena {

	public static final int LONGITUD_MINIMA = 12;
	public static final int LONGITUD_MAXIMA = 72;

	private static final Set<String> CLAVES_COMUNES = Set.of(
			"123456", "1234567", "12345678", "123456789", "1234567890",
			"password", "password1", "contrasena", "contraseña", "qwerty",
			"qwerty123", "abc123", "admin", "administrador", "iloveyou",
			"bienvenido", "welcome", "letmein", "dragon", "monkey",
			"111111", "000000", "utp", "adaptcode", "kahoot");

	public void validar(String contrasena, String correo, String... datosPersonales) {
		if (contrasena == null || contrasena.isBlank()) {
			throw new SolicitudInvalidaException("La contraseña es obligatoria.");
		}
		List<String> faltantes = new ArrayList<>();

		if (contrasena.length() < LONGITUD_MINIMA) {
			faltantes.add("al menos " + LONGITUD_MINIMA + " caracteres");
		}
		if (contrasena.getBytes(StandardCharsets.UTF_8).length > LONGITUD_MAXIMA) {
			faltantes.add("como máximo " + LONGITUD_MAXIMA + " bytes (BCrypt)");
		}
		if (contrasena.codePoints().noneMatch(Character::isLowerCase)) {
			faltantes.add("al menos una letra minúscula");
		}
		if (contrasena.codePoints().noneMatch(Character::isUpperCase)) {
			faltantes.add("al menos una letra mayúscula");
		}
		if (contrasena.codePoints().noneMatch(Character::isDigit)) {
			faltantes.add("al menos un número");
		}
		if (contrasena.codePoints().noneMatch(
				c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c))) {
			faltantes.add("al menos un símbolo (!@#$%...)");
		}
		if (contrasena.codePoints().anyMatch(Character::isWhitespace)) {
			faltantes.add("sin espacios");
		}

		String normalizada = contrasena.toLowerCase(Locale.ROOT);
		if (esClaveComun(normalizada)) {
			faltantes.add("no ser una contraseña común");
		}

		if (contieneDato(normalizada, correo)) {
			faltantes.add("no contener tu correo");
		}
		for (String dato : datosPersonales) {
			if (contieneDato(normalizada, dato)) {
				faltantes.add("no contener tus datos personales");
				break;
			}
		}

		if (!faltantes.isEmpty()) {
			throw new SolicitudInvalidaException(
					"La contraseña debe cumplir: " + String.join(", ", faltantes) + ".");
		}
	}

	private boolean esClaveComun(String normalizada) {
		if (CLAVES_COMUNES.contains(normalizada)) {
			return true;
		}
		return normalizada.contains("password")
				|| normalizada.contains("contrasena")
				|| normalizada.contains("contraseña")
				|| normalizada.contains("qwerty")
				|| normalizada.contains("123456")
				|| normalizada.contains("adaptcode")
				|| normalizada.contains("kahoot");
	}

	private boolean contieneDato(String contrasenaNormalizada, String dato) {
		if (dato == null || dato.isBlank()) {
			return false;
		}
		String valor = dato.trim().toLowerCase(Locale.ROOT);
		if (valor.length() < 3) {
			return false;
		}
		int arroba = valor.indexOf('@');
		if (arroba > 0) {
			String local = valor.substring(0, arroba);
			if (local.length() >= 3 && contrasenaNormalizada.contains(local)) {
				return true;
			}
		}
		return contrasenaNormalizada.contains(valor);
	}
}
