package com.adaptcode.utp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Confirmación de baja de cuenta. Se exige la contraseña actual para que un
 * token robado no pueda destruir la cuenta por sí solo.
 */
public record EliminarCuentaRequest(
		@NotBlank(message = "La contraseña es obligatoria.")
		@Size(max = 72, message = "La contraseña no es válida.")
		String contrasena) {
}
