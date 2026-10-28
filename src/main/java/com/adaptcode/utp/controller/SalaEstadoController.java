package com.adaptcode.utp.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.adaptcode.utp.dto.response.EstadoPreguntaResponse;
import com.adaptcode.utp.dto.response.PreguntaSalaResponse;
import com.adaptcode.utp.model.RolUsuario;
import com.adaptcode.utp.security.UsuarioPrincipal;
import com.adaptcode.utp.service.AutorizacionSalaService;
import com.adaptcode.utp.service.SalaService;

import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

/**
 * Estado del cronómetro de la pregunta actual para docentes propietarios y
 * estudiantes con participación en la sala. El cliente puede sincronizar la
 * cuenta regresiva, pero el servidor sigue siendo la autoridad del tiempo.
 */
@RestController
@RequestMapping("/api/salas/{salaId}")
@Validated
@RequiredArgsConstructor
public class SalaEstadoController {

	private final SalaService salaService;
	private final AutorizacionSalaService autorizacionSalaService;

	@GetMapping("/estado-pregunta")
	public ResponseEntity<EstadoPreguntaResponse> estadoPregunta(
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long salaId,
			@AuthenticationPrincipal UsuarioPrincipal principal) {
		autorizacionSalaService.validarLectura(salaId, principal);
		return ResponseEntity.ok(salaService.estadoPregunta(salaId));
	}

	/**
	 * Pregunta y alternativas que se están jugando. No expone cuál es la
	 * correcta ni retroalimentación: solo lo necesario para responder.
	 */
	@GetMapping("/pregunta-actual")
	public ResponseEntity<PreguntaSalaResponse> preguntaActual(
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long salaId,
			@AuthenticationPrincipal UsuarioPrincipal principal) {
		autorizacionSalaService.validarLectura(salaId, principal);
		Long estudianteId = principal.getRol() == RolUsuario.ESTUDIANTE_UTP
				? principal.getId()
				: null;
		return ResponseEntity.ok(salaService.preguntaActual(salaId, estudianteId));
	}
}
