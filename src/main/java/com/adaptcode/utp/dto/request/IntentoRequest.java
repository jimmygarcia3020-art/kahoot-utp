package com.adaptcode.utp.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Registro de una solución asistida. El cliente solo indica qué reto completó y
 * cuánto tardó: el servidor decide el acierto, la ayuda y el número de intento.
 * Los intentos previos deben existir (mínimo tres) para poder usar la solución.
 */
public record IntentoRequest(
		@NotNull(message = "El micro-reto es obligatorio.") @Positive(message = "El identificador debe ser positivo.") Long microRetoId,
		@PositiveOrZero(message = "El tiempo no puede ser negativo.") Long tiempoMs) {
}
