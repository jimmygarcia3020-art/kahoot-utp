package com.adaptcode.utp.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.adaptcode.utp.dto.request.RespuestaSalaRequest;
import com.adaptcode.utp.dto.response.RespuestaSalaResponse;
import com.adaptcode.utp.model.RolUsuario;
import com.adaptcode.utp.security.UsuarioPrincipal;
import com.adaptcode.utp.service.AutorizacionSalaService;
import com.adaptcode.utp.service.ParticipacionSalaService;
import com.adaptcode.utp.service.RespuestaSalaService;
import com.adaptcode.utp.util.Paginacion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/salas/{salaId}/respuestas")
@Validated
@RequiredArgsConstructor
public class RespuestaSalaController {

	private final RespuestaSalaService respuestaSalaService;
	private final ParticipacionSalaService participacionSalaService;
	private final AutorizacionSalaService autorizacionSalaService;

	@PostMapping
	public ResponseEntity<RespuestaSalaResponse> registrar(
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long salaId,
			@AuthenticationPrincipal UsuarioPrincipal principal,
			@Valid @RequestBody RespuestaSalaRequest request) {
		validarAcceso(principal, salaId, request.participacionId());
		RespuestaSalaResponse response = respuestaSalaService.registrar(
				salaId, request);
		return new ResponseEntity<>(response, HttpStatus.CREATED);
	}

	@GetMapping
	public ResponseEntity<?> listar(
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long salaId,
			@Positive(message = "El identificador debe ser positivo.") @RequestParam Long participacionId,
			@RequestParam(required = false) @PositiveOrZero(message = "La página no puede ser negativa.") Integer page,
			@RequestParam(required = false) @Positive(message = "El tamaño debe ser positivo.") @Max(value = 100, message = "El tamaño máximo es 100.") Integer size,
			@AuthenticationPrincipal UsuarioPrincipal principal) {
		validarAcceso(principal, salaId, participacionId);
		return ResponseEntity.ok(Paginacion.responder(
				respuestaSalaService.listarPorParticipacion(salaId, participacionId),
				page, size));
	}

	@GetMapping("/{id}")
	public ResponseEntity<RespuestaSalaResponse> mostrar(
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long salaId,
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long id,
			@AuthenticationPrincipal UsuarioPrincipal principal) {
		RespuestaSalaResponse response = respuestaSalaService.mostrarPorId(
				salaId, id);
		// La respuesta ya se cargó, pero no se devuelve si no es del estudiante.
		validarAcceso(principal, salaId, response.participacionId());
		return ResponseEntity.ok(response);
	}

	@GetMapping("/por-pregunta")
	public ResponseEntity<RespuestaSalaResponse> mostrarPorPregunta(
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long salaId,
			@Positive(message = "El identificador debe ser positivo.") @RequestParam Long participacionId,
			@Positive(message = "El identificador debe ser positivo.") @RequestParam Long preguntaId,
			@AuthenticationPrincipal UsuarioPrincipal principal) {
		validarAcceso(principal, salaId, participacionId);
		return ResponseEntity.ok(respuestaSalaService.mostrarPorParticipacionYPregunta(
				salaId, participacionId, preguntaId));
	}

	private void validarAcceso(UsuarioPrincipal principal, Long salaId,
			Long participacionId) {
		if (principal != null
				&& principal.getRol() == RolUsuario.ESTUDIANTE_UTP) {
			participacionSalaService.validarPropietario(salaId, participacionId,
					principal.getId());
			return;
		}
		// El docente solo puede leer respuestas de sus propias salas.
		autorizacionSalaService.validarLectura(salaId, principal);
	}
}
