package com.adaptcode.utp.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.slf4j.MDC;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import com.adaptcode.utp.config.RateLimitFilter;
import com.adaptcode.utp.config.TraceIdFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Configuración de seguridad: API stateless con JWT, contraseñas BCrypt y
 * autorización por rol. El catálogo y el alta/inicio de sesión son públicos.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	public static final String ROLE_DOCENTE = "DOCENTE_UTP";
	public static final String ROLE_ESTUDIANTE = "ESTUDIANTE_UTP";

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final RateLimitFilter rateLimitFilter;

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session
						.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.headers(headers -> headers
						.frameOptions(frame -> frame.deny())
						.contentTypeOptions(Customizer.withDefaults())
						.referrerPolicy(referrer -> referrer.policy(
								ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
						.httpStrictTransportSecurity(hsts -> hsts
								.includeSubDomains(true)
								.maxAgeInSeconds(31_536_000))
						.contentSecurityPolicy(csp -> csp.policyDirectives(
								"default-src 'self'; "
										+ "base-uri 'self'; "
										+ "form-action 'self'; "
										+ "frame-ancestors 'none'; "
										+ "object-src 'none'; "
										+ "img-src 'self' data:; "
										+ "font-src 'self' https://fonts.gstatic.com; "
										+ "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
										+ "script-src 'self'; "
										+ "connect-src 'self'")))
				.authorizeHttpRequests(auth -> auth
						// Frontend: documento inicial, favicon y assets estaticos son publicos.
						.requestMatchers(HttpMethod.GET, "/", "/index.html",
								"/favicon.ico", "/favicon.svg", "/assets/**")
						.permitAll()
						// Documentacion OpenAPI y Swagger UI (se puede desactivar
						// con springdoc.api-docs.enabled=false en produccion).
						.requestMatchers("/v3/api-docs/**", "/swagger-ui/**",
								"/swagger-ui.html")
						.permitAll()
						// Sondas de salud para el balanceador. Solo exponen el
						// estado; los detalles requieren autenticacion.
						.requestMatchers(HttpMethod.GET, "/actuator/health",
								"/actuator/health/**")
						.permitAll()
						.requestMatchers(HttpMethod.POST, "/api/auth/**").permitAll()
						// El flujo SSE se abre con un ticket de un solo uso porque
						// EventSource no puede enviar Authorization. El ticket se
						// valida en el controlador y esta atado a la sala.
						.requestMatchers(HttpMethod.GET, "/api/salas/*/eventos")
						.permitAll()
						.requestMatchers(HttpMethod.POST,
								"/api/salas/*/eventos/ticket")
						.hasAnyRole(ROLE_DOCENTE, ROLE_ESTUDIANTE)
						.requestMatchers(HttpMethod.GET, "/api/cursos/**",
								"/api/micro-retos/**").permitAll()
						.requestMatchers("/api/docentes/**").hasRole(ROLE_DOCENTE)
						.requestMatchers(HttpMethod.POST,
								"/api/salas/*/participaciones/*/expulsar",
								"/api/salas/*/participaciones/*/readmitir")
						.hasRole(ROLE_DOCENTE)
						.requestMatchers(HttpMethod.GET, "/api/salas/*/ranking",
								"/api/salas/*/estado-pregunta",
								"/api/salas/*/pregunta-actual")
						.hasAnyRole(ROLE_DOCENTE, ROLE_ESTUDIANTE)
						.requestMatchers(HttpMethod.GET, "/api/salas/*/participaciones/**",
								"/api/salas/*/respuestas/**")
						.hasAnyRole(ROLE_DOCENTE, ROLE_ESTUDIANTE)
						.requestMatchers("/api/salas/**").hasRole(ROLE_ESTUDIANTE)
						.anyRequest().authenticated())
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint(unauthorizedEntryPoint())
						.accessDeniedHandler(accessDeniedHandler()))
				.addFilterBefore(jwtAuthenticationFilter,
						UsernamePasswordAuthenticationFilter.class)
				.addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class);
		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		// Coste 12: ~250 ms por hash en hardware típico; frena ataques de diccionario.
		return new BCryptPasswordEncoder(12);
	}

	@Bean
	public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(
			JwtAuthenticationFilter filter) {
		FilterRegistrationBean<JwtAuthenticationFilter> registration =
				new FilterRegistrationBean<>(filter);
		registration.setEnabled(false);
		return registration;
	}

	/**
	 * El filtro de límite se ejecuta dentro de la cadena de seguridad para ver
	 * al usuario autenticado. Se desactiva su registro automático como filtro
	 * de servlet para no aplicarlo dos veces por petición.
	 */
	@Bean
	public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
			RateLimitFilter filter) {
		FilterRegistrationBean<RateLimitFilter> registration =
				new FilterRegistrationBean<>(filter);
		registration.setEnabled(false);
		return registration;
	}

	@Bean
	public AuthenticationManager authenticationManager(
			AuthenticationConfiguration configuration) throws Exception {
		return configuration.getAuthenticationManager();
	}

	private AuthenticationEntryPoint unauthorizedEntryPoint() {
		return (request, response, ex) -> escribirError(request, response,
				HttpServletResponse.SC_UNAUTHORIZED, "NO_AUTENTICADO",
				"No autenticado. Debe iniciar sesión.");
	}

	private AccessDeniedHandler accessDeniedHandler() {
		return (request, response, ex) -> escribirError(request, response,
				HttpServletResponse.SC_FORBIDDEN, "ACCESO_DENEGADO",
				"Acceso denegado para su rol.");
	}

	/**
	 * Los errores generados por la cadena de filtros no pasan por el
	 * {@code @RestControllerAdvice}; se emite el mismo contrato para que el
	 * cliente reciba siempre la misma forma.
	 */
	private void escribirError(HttpServletRequest request,
			HttpServletResponse response, int status, String code, String mensaje)
			throws IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		String traceId = MDC.get(TraceIdFilter.MDC_KEY);
		response.getWriter().write("{\"timestamp\":\"" + Instant.now()
				+ "\",\"status\":" + status
				+ ",\"code\":\"" + code
				+ "\",\"message\":\"" + mensaje.replace("\"", "\\\"")
				+ "\",\"path\":\"" + request.getRequestURI().replace("\"", "\\\"")
				+ "\",\"traceId\":\"" + (traceId == null ? "" : traceId) + "\"}");
	}
}
