package com.adaptcode.utp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequest(
		@NotBlank(message = "El token de refresco es obligatorio.")
		@Size(max = 4096, message = "El token de refresco no es válido.")
		String refreshToken) {
}
