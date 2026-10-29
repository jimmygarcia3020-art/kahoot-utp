package com.adaptcode.utp.config;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Asigna un identificador de correlación a cada petición, lo publica en el
 * encabezado {@code X-Request-Id} y lo deja en el MDC para que aparezca en los
 * logs. Si el cliente envía un id válido se respeta (útil detrás de un proxy).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Request-Id";
	public static final String MDC_KEY = "traceId";
	private static final Pattern ID_VALIDO = Pattern.compile("^[A-Za-z0-9._-]{8,64}$");

	@Override
	protected void doFilterInternal(HttpServletRequest request,
			HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String solicitado = request.getHeader(HEADER);
		String traceId = solicitado != null && ID_VALIDO.matcher(solicitado).matches()
				? solicitado
				: UUID.randomUUID().toString();
		MDC.put(MDC_KEY, traceId);
		response.setHeader(HEADER, traceId);
		try {
			filterChain.doFilter(request, response);
		} finally {
			MDC.remove(MDC_KEY);
		}
	}
}
