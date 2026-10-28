package com.adaptcode.utp.config;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.adaptcode.utp.security.UsuarioPrincipal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Límite de peticiones por identidad y por grupo de rutas. Frena fuerza bruta,
 * enumeración de PINs y abuso general de la API.
 *
 * <p>El filtro se ejecuta dentro de la cadena de seguridad, después de
 * {@link com.adaptcode.utp.security.JwtAuthenticationFilter}, para poder usar
 * el usuario autenticado como identidad. Así una clase entera detrás de la
 * misma IP/NAT no comparte el cupo: cada estudiante tiene el suyo. Las rutas de
 * autenticación se limitan siempre por IP porque en ellas todavía no hay un
 * usuario verificado.</p>
 *
 * <p>El estado es en memoria (ventana fija de un minuto). Con varias instancias
 * cada una limita por separado; para un límite global compartido debe
 * sustituirse por Redis. No se confía en {@code X-Forwarded-For} salvo que se
 * active {@code seguridad.rate-limit-confiar-proxy}, porque el cliente puede
 * falsificarlo y saltarse el límite.</p>
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

	private static final long VENTANA_MS = 60_000L;
	private static final long LIMPIEZA_CADA_MS = 300_000L;

	private final boolean habilitado;
	private final boolean confiarProxy;
	private final int limiteGeneral;
	private final int limiteAuth;
	private final int limitePin;
	private final int limiteJuego;

	private final ConcurrentHashMap<String, Ventana> ventanas = new ConcurrentHashMap<>();
	private final AtomicLong ultimaLimpieza = new AtomicLong(System.currentTimeMillis());

	public RateLimitFilter(
			@Value("${seguridad.rate-limit-habilitado:true}") boolean habilitado,
			@Value("${seguridad.rate-limit-confiar-proxy:false}") boolean confiarProxy,
			@Value("${seguridad.rate-limit-general-por-minuto:240}") int limiteGeneral,
			@Value("${seguridad.rate-limit-auth-por-minuto:60}") int limiteAuth,
			@Value("${seguridad.rate-limit-pin-por-minuto:60}") int limitePin,
			@Value("${seguridad.rate-limit-juego-por-minuto:300}") int limiteJuego) {
		this.habilitado = habilitado;
		this.confiarProxy = confiarProxy;
		this.limiteGeneral = Math.max(1, limiteGeneral);
		this.limiteAuth = Math.max(1, limiteAuth);
		this.limitePin = Math.max(1, limitePin);
		this.limiteJuego = Math.max(1, limiteJuego);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request,
			HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (!habilitado || !request.getRequestURI().startsWith("/api/")) {
			filterChain.doFilter(request, response);
			return;
		}
		String grupo = grupo(request);
		int limite = limiteDe(grupo);
		String clave = grupo + "|" + identidad(request, grupo);
		long ahora = System.currentTimeMillis();
		Ventana ventana = ventanas.compute(clave, (k, actual) -> {
			if (actual == null || ahora - actual.inicio >= VENTANA_MS) {
				return new Ventana(ahora);
			}
			actual.peticiones++;
			return actual;
		});
		if (ventana.peticiones > limite) {
			long espera = Math.max(1,
					(VENTANA_MS - (ahora - ventana.inicio) + 999) / 1000);
			escribirLimite(request, response, limite, espera);
			return;
		}
		response.setHeader("X-RateLimit-Limit", String.valueOf(limite));
		response.setHeader("X-RateLimit-Remaining",
				String.valueOf(Math.max(0, limite - ventana.peticiones)));
		limpiarSiCorresponde(ahora);
		filterChain.doFilter(request, response);
	}

	private String grupo(HttpServletRequest request) {
		String ruta = request.getRequestURI();
		if (ruta.startsWith("/api/auth/") || ruta.equals("/api/auth")
				|| ruta.endsWith("/eliminar")) {
			return "auth";
		}
		if (ruta.startsWith("/api/salas/por-pin")
				|| ruta.startsWith("/api/salas/buscar")) {
			return "pin";
		}
		if (ruta.startsWith("/api/salas/")) {
			return "juego";
		}
		return "general";
	}

	private int limiteDe(String grupo) {
		return switch (grupo) {
			case "auth" -> limiteAuth;
			case "pin" -> limitePin;
			case "juego" -> limiteJuego;
			default -> limiteGeneral;
		};
	}

	/**
	 * Las rutas de autenticación siempre se limitan por IP: el usuario todavía
	 * no está verificado. En el resto, si hay un principal autenticado se usa su
	 * identificador para que el cupo sea individual aunque comparta red.
	 */
	private String identidad(HttpServletRequest request, String grupo) {
		if (!"auth".equals(grupo)) {
			Authentication autenticacion = SecurityContextHolder.getContext()
					.getAuthentication();
			if (autenticacion != null && autenticacion.isAuthenticated()
					&& autenticacion.getPrincipal() instanceof UsuarioPrincipal principal) {
				return "u:" + principal.getId();
			}
		}
		return "ip:" + ipDe(request);
	}

	private String ipDe(HttpServletRequest request) {
		if (confiarProxy) {
			String reenviada = request.getHeader("X-Forwarded-For");
			if (reenviada != null && !reenviada.isBlank()) {
				// El primer valor es el cliente original; el resto son proxies.
				return reenviada.split(",")[0].trim();
			}
		}
		String ip = request.getRemoteAddr();
		return ip == null || ip.isBlank() ? "desconocida" : ip;
	}

	private void limpiarSiCorresponde(long ahora) {
		long ultima = ultimaLimpieza.get();
		if (ahora - ultima < LIMPIEZA_CADA_MS
				|| !ultimaLimpieza.compareAndSet(ultima, ahora)) {
			return;
		}
		ventanas.entrySet().removeIf(entrada ->
				ahora - entrada.getValue().inicio >= VENTANA_MS);
	}

	private void escribirLimite(HttpServletRequest request,
			HttpServletResponse response, int limite, long segundos)
			throws IOException {
		response.setStatus(429);
		response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(segundos));
		response.setHeader("X-RateLimit-Limit", String.valueOf(limite));
		response.setHeader("X-RateLimit-Remaining", "0");
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		String traceId = MDC.get(TraceIdFilter.MDC_KEY);
		response.getWriter().write("{\"timestamp\":\"" + Instant.now()
				+ "\",\"status\":429,\"code\":\"DEMASIADOS_INTENTOS\","
				+ "\"message\":\"Demasiadas solicitudes. Espere un momento y "
				+ "vuelva a intentarlo.\",\"path\":\""
				+ escapar(request.getRequestURI())
				+ "\",\"traceId\":\"" + (traceId == null ? "" : traceId)
				+ "\",\"fieldErrors\":null}");
	}

	private String escapar(String valor) {
		return valor == null ? "" : valor.replace("\"", "\\\"");
	}

	private static final class Ventana {
		private final long inicio;
		private long peticiones;

		private Ventana(long inicio) {
			this.inicio = inicio;
			this.peticiones = 1;
		}
	}
}
