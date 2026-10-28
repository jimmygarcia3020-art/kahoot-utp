package com.adaptcode.utp.dto.response;

import java.time.Instant;
import java.util.Map;

import org.slf4j.MDC;

import com.adaptcode.utp.config.TraceIdFilter;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Contrato único de error de la API. Todas las respuestas 4xx/5xx comparten la
 * misma forma para que el frontend y los clientes puedan tratarlas sin casos
 * especiales:
 *
 * <pre>
 * {
 *   "timestamp": "2026-10-26T12:00:00Z",
 *   "status": 400,
 *   "code": "VALIDACION",
 *   "message": "La solicitud contiene datos inválidos.",
 *   "path": "/api/auth/registro",
 *   "traceId": "4f1c...",
 *   "fieldErrors": { "correo": "El formato del correo no es válido." }
 * }
 * </pre>
 *
 * {@code fieldErrors} es {@code null} cuando el error no es de validación.
 */
public record ApiErrorResponse(
		Instant timestamp,
		int status,
		String code,
		String message,
		String path,
		String traceId,
		Map<String, String> fieldErrors) {

	public static ApiErrorResponse de(int status, String code, String message,
			HttpServletRequest request) {
		return new ApiErrorResponse(Instant.now(), status, code, message,
				request == null ? null : request.getRequestURI(),
				MDC.get(TraceIdFilter.MDC_KEY), null);
	}

	public static ApiErrorResponse de(int status, String code, String message,
			HttpServletRequest request, Map<String, String> fieldErrors) {
		return new ApiErrorResponse(Instant.now(), status, code, message,
				request == null ? null : request.getRequestURI(),
				MDC.get(TraceIdFilter.MDC_KEY), fieldErrors);
	}
}
