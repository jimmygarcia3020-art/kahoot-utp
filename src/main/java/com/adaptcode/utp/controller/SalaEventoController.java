package com.adaptcode.utp.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.adaptcode.utp.dto.response.SalaEventoTicketResponse;
import com.adaptcode.utp.security.UsuarioPrincipal;
import com.adaptcode.utp.service.AutorizacionSalaService;
import com.adaptcode.utp.service.SalaEventoService;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * Flujo en vivo de una sala mediante Server-Sent Events.
 *
 * <p>DTO / stream: primero {@code POST .../eventos/ticket} (autenticado con
 * Bearer) y luego {@code GET .../eventos?ticket=...} (EventSource no admite
 * cabeceras). El ticket es de un solo uso y expira en 60 segundos.</p>
 */
@RestController
@RequestMapping("/api/salas/{salaId}/eventos")
@Validated
@RequiredArgsConstructor
public class SalaEventoController {

	private final SalaEventoService salaEventoService;
	private final AutorizacionSalaService autorizacionSalaService;

	@PostMapping("/ticket")
	public ResponseEntity<SalaEventoTicketResponse> ticket(
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long salaId,
			@AuthenticationPrincipal UsuarioPrincipal principal) {
		// Docente propietario o estudiante con participación en la sala.
		autorizacionSalaService.validarLectura(salaId, principal);
		return ResponseEntity.ok(
				salaEventoService.crearTicket(salaId, principal.getId()));
	}

	@GetMapping
	public ResponseEntity<SseEmitter> eventos(
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long salaId,
			@NotBlank(message = "El ticket es obligatorio.")
			@Size(max = 128, message = "El ticket no es válido.")
			@RequestParam String ticket) {
		salaEventoService.canjearTicket(ticket, salaId);
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
				.header("X-Accel-Buffering", "no")
				.body(salaEventoService.suscribir(salaId));
	}
}
