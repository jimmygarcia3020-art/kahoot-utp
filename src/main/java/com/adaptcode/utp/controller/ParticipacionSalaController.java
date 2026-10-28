package com.adaptcode.utp.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.adaptcode.utp.dto.request.ParticipacionSalaRequest;
import com.adaptcode.utp.dto.response.ParticipacionSalaResponse;
import com.adaptcode.utp.model.RolUsuario;
import com.adaptcode.utp.security.UsuarioPrincipal;
import com.adaptcode.utp.service.AutorizacionSalaService;
import com.adaptcode.utp.service.ParticipacionSalaService;
import com.adaptcode.utp.util.Paginacion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/salas/{salaId}/participaciones")
@Validated
@RequiredArgsConstructor
public class ParticipacionSalaController {

	private final ParticipacionSalaService participacionSalaService;
	private final AutorizacionSalaService autorizacionSalaService;

	@PostMapping
	public ResponseEntity<ParticipacionSalaResponse> unirse(
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long salaId,
			@AuthenticationPrincipal UsuarioPrincipal principal,
			@Valid @RequestBody ParticipacionSalaRequest request) {
		// Impide que un estudiante se inscriba suplantando a otro.
		if (esEstudiante(principal)
				&& !principal.getId().equals(request.estudianteId())) {
			throw new AccessDeniedException(
					"No puede registrarse en nombre de otro estudiante.");
		}
		ParticipacionSalaResponse response = participacionSalaService.unirse(
				salaId, request);
		return new ResponseEntity<>(response, HttpStatus.CREATED);
	}

	@GetMapping
	public ResponseEntity<?> listar(
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long salaId,
			@RequestParam(required = false) @PositiveOrZero(message = "La página no puede ser negativa.") Integer page,
			@RequestParam(required = false) @Positive(message = "El tamaño debe ser positivo.") @Max(value = 100, message = "El tamaño máximo es 100.") Integer size,
			@AuthenticationPrincipal UsuarioPrincipal principal) {
		// Un estudiante solo ve su propia participación; el docente ve la sala completa.
		if (esEstudiante(principal)) {
			return ResponseEntity.ok(Paginacion.responder(
					participacionSalaService.listarPorSala(salaId, principal.getId()),
					page, size));
		}
		// Un docente solo puede leer participaciones de sus propias salas.
		autorizacionSalaService.validarDocentePropietario(salaId, principal.getId());
		return ResponseEntity.ok(Paginacion.responder(
				participacionSalaService.listarPorSala(salaId), page, size));
	}

	@GetMapping("/{id}")
	public ResponseEntity<ParticipacionSalaResponse> mostrar(
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long salaId,
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long id,
			@AuthenticationPrincipal UsuarioPrincipal principal) {
		if (esEstudiante(principal)) {
			participacionSalaService.validarPropietario(salaId, id,
					principal.getId());
		} else {
			autorizacionSalaService.validarDocentePropietario(salaId,
					principal.getId());
		}
		return ResponseEntity.ok(
				participacionSalaService.mostrarPorId(salaId, id));
	}

	@PostMapping("/{id}/expulsar")
	public ResponseEntity<ParticipacionSalaResponse> expulsar(
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long salaId,
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long id,
			@Positive(message = "El identificador debe ser positivo.") @RequestParam Long docenteId) {
		return ResponseEntity.ok(
				participacionSalaService.expulsar(docenteId, salaId, id));
	}

	@PostMapping("/{id}/readmitir")
	public ResponseEntity<ParticipacionSalaResponse> readmitir(
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long salaId,
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long id,
			@Positive(message = "El identificador debe ser positivo.") @RequestParam Long docenteId) {
		return ResponseEntity.ok(
				participacionSalaService.readmitir(docenteId, salaId, id));
	}

	private boolean esEstudiante(UsuarioPrincipal principal) {
		return principal != null && principal.getRol() == RolUsuario.ESTUDIANTE_UTP;
	}
}
