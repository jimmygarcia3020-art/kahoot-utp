package com.adaptcode.utp.dto.response;

/**
 * Alternativa visible durante la partida. Nunca incluye {@code correcta} ni
 * retroalimentación: la calificación es exclusiva del servidor.
 */
public record AlternativaSalaResponse(
		Long id,
		String texto,
		String codigo,
		String lenguaje,
		int ordenVisual) {
}
