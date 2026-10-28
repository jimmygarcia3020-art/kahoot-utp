package com.adaptcode.utp.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
		@NotBlank(message = "El correo es obligatorio.")
		@Email(message = "El formato del correo no es válido.")
		@Size(max = 254, message = "El correo no debe superar los 254 caracteres.")
		String correo,

		@NotBlank(message = "La contraseña es obligatoria.")
		@Size(max = 72, message = "La contraseña no debe superar los 72 caracteres.")
		String contrasena) {
}
