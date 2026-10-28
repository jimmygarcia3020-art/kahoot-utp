package com.adaptcode.utp.service;

import com.adaptcode.utp.dto.request.LoginRequest;
import com.adaptcode.utp.dto.request.RecuperarContrasenaRequest;
import com.adaptcode.utp.dto.request.ReenviarCodigoRequest;
import com.adaptcode.utp.dto.request.RefreshTokenRequest;
import com.adaptcode.utp.dto.request.RegistroRequest;
import com.adaptcode.utp.dto.request.RestablecerContrasenaRequest;
import com.adaptcode.utp.dto.request.VerificarCodigoRequest;
import com.adaptcode.utp.dto.response.AuthResponse;
import com.adaptcode.utp.dto.response.CodigoResponse;
import com.adaptcode.utp.dto.response.MensajeResponse;
import com.adaptcode.utp.dto.response.RegistroResponse;

public interface AuthService {

	RegistroResponse registrar(RegistroRequest request);

	AuthResponse login(LoginRequest request);

	/**
	 * Renueva el access token con un refresh token de un solo uso. El refresh
	 * token presentado queda revocado y se emite uno nuevo (rotación).
	 */
	AuthResponse refrescar(RefreshTokenRequest request);

	/** Revoca el refresh token indicado. Operación idempotente. */
	MensajeResponse cerrarSesion(RefreshTokenRequest request);

	MensajeResponse verificarCuenta(VerificarCodigoRequest request);

	CodigoResponse reenviarCodigoVerificacion(ReenviarCodigoRequest request);

	CodigoResponse solicitarRecuperacion(RecuperarContrasenaRequest request);

	MensajeResponse restablecerContrasena(RestablecerContrasenaRequest request);
}
