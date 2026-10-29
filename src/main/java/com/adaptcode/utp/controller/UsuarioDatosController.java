package com.adaptcode.utp.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.adaptcode.utp.dto.request.EliminarCuentaRequest;
import com.adaptcode.utp.dto.response.DatosPersonalesResponse;
import com.adaptcode.utp.dto.response.MensajeResponse;
import com.adaptcode.utp.exception.ResourceNotFoundException;
import com.adaptcode.utp.security.UsuarioPrincipal;
import com.adaptcode.utp.service.DatosPersonalesService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

/**
 * Portabilidad y supresión de datos personales. Solo el propio usuario puede
 * exportar o eliminar su cuenta; el interceptor de propiedad responde 403 ante
 * el identificador de otro usuario, sin exponer sus datos.
 */
@RestController
@RequestMapping("/api/usuarios/{usuarioId}")
@Validated
@RequiredArgsConstructor
public class UsuarioDatosController {

	private final DatosPersonalesService datosPersonalesService;

	@GetMapping("/datos")
	public ResponseEntity<DatosPersonalesResponse> exportar(
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long usuarioId,
			@AuthenticationPrincipal UsuarioPrincipal principal) {
		validarPropietario(usuarioId, principal);
		return ResponseEntity.ok(datosPersonalesService.exportar(usuarioId));
	}

	@PostMapping("/eliminar")
	public ResponseEntity<MensajeResponse> eliminar(
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long usuarioId,
			@AuthenticationPrincipal UsuarioPrincipal principal,
			@Valid @RequestBody EliminarCuentaRequest request) {
		validarPropietario(usuarioId, principal);
		return ResponseEntity.ok(datosPersonalesService.eliminar(usuarioId, request));
	}

	private void validarPropietario(Long usuarioId, UsuarioPrincipal principal) {
		if (principal == null || !usuarioId.equals(principal.getId())) {
			throw new ResourceNotFoundException("El usuario no existe.");
		}
	}
}
