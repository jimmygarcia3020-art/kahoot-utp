package com.adaptcode.utp.dto.response;

import java.time.Instant;

/**
 * Ticket efímero para abrir el flujo SSE. {@code EventSource} no permite
 * enviar el JWT en una cabecera, así que el cliente canjea este ticket en la
 * URL del flujo. Es de un solo uso, está atado a la sala y expira en 60 s.
 */
public record SalaEventoTicketResponse(
		String ticket,
		Instant expiraEn,
		long vigenciaMs) {
}
