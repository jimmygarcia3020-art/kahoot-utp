package com.adaptcode.utp.config;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import com.adaptcode.utp.security.UsuarioPrincipal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Evita IDOR: si la ruta o los parámetros declaran un identificador de usuario
 * ({@code usuarioId}, {@code docenteId} o {@code estudianteId}) distinto al del
 * token, responde 403. El frontend puede seguir usando sus rutas actuales.
 */
@Component
public class PropietarioInterceptor implements HandlerInterceptor {

	private static final List<String> CLAVES_RUTA = List.of("usuarioId",
			"docenteId", "estudianteId");
	private static final List<String> CLAVES_PARAMETRO = List.of("usuarioId",
			"docenteId", "estudianteId");

	@Override
	public boolean preHandle(HttpServletRequest request,
			HttpServletResponse response, Object handler) throws IOException {
		Authentication authentication = SecurityContextHolder.getContext()
				.getAuthentication();
		if (authentication == null
				|| !(authentication.getPrincipal() instanceof UsuarioPrincipal principal)) {
			return true;
		}
		String id = String.valueOf(principal.getId());

		Object atributo = request
				.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
		if (atributo instanceof Map<?, ?> variables) {
			for (String clave : CLAVES_RUTA) {
				Object valor = variables.get(clave);
				if (valor != null && !id.equals(valor.toString())) {
					return denegar(request, response);
				}
			}
		}

		for (String clave : CLAVES_PARAMETRO) {
			String valor = request.getParameter(clave);
			if (valor != null && !valor.isBlank() && !id.equals(valor)) {
				return denegar(request, response);
			}
		}
		return true;
	}

	private boolean denegar(HttpServletRequest request, HttpServletResponse response)
			throws IOException {
		response.setStatus(HttpServletResponse.SC_FORBIDDEN);
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");
		String traceId = org.slf4j.MDC.get(TraceIdFilter.MDC_KEY);
		response.getWriter().write(
				"{\"timestamp\":\"" + java.time.Instant.now()
						+ "\",\"status\":403,\"code\":\"ACCESO_DENEGADO\","
						+ "\"message\":\"No puede acceder a recursos de otro usuario.\","
						+ "\"path\":\""
						+ request.getRequestURI().replace("\"", "\\\"")
						+ "\",\"traceId\":\""
						+ (traceId == null ? "" : traceId)
						+ "\",\"fieldErrors\":null}");
		return false;
	}
}
