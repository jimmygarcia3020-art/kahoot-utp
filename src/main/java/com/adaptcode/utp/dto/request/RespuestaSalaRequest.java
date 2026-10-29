package com.adaptcode.utp.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Envío de una respuesta de sala.
 *
 * <p>El servidor califica contra {@code AlternativaDocente.correcta} y mide el
 * tiempo desde que el docente habilitó la pregunta. {@code acierto} y
 * {@code tiempoMs} se conservan solo por compatibilidad con clientes antiguos:
 * se ignoran y no influyen en el puntaje.</p>
 */
public record RespuestaSalaRequest(
		@NotNull(message = "La participación es obligatoria.") @Positive(message = "El identificador debe ser positivo.") Long participacionId,
		@NotNull(message = "La pregunta es obligatoria.") @Positive(message = "El identificador debe ser positivo.") Long preguntaId,
		@Positive(message = "El identificador debe ser positivo.") Long alternativaId,
		@Deprecated Boolean acierto,
		Boolean omitida,
		@Deprecated @PositiveOrZero(message = "El tiempo no puede ser negativo.") Long tiempoMs) {
}
