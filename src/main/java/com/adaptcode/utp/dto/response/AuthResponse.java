package com.adaptcode.utp.dto.response;

import java.time.LocalDate;

import com.adaptcode.utp.model.GeneroUsuario;
import com.adaptcode.utp.model.RolUsuario;

/**
 * Respuesta de autenticación. {@code token} es el access token de corta vida
 * (15 minutos); {@code refreshToken} permite renovarlo sin volver a enviar la
 * contraseña. El refresh token se rota en cada uso y puede revocarse con
 * {@code POST /api/auth/logout}.
 */
public record AuthResponse(
		String token,
		String tipoToken,
		long expiraEnMs,
		Long usuarioId,
		String correo,
		String nombres,
		String apellidos,
		RolUsuario rol,
		GeneroUsuario genero,
		LocalDate fechaNacimiento,
		String refreshToken,
		long refreshExpiraEnMs) {
}
