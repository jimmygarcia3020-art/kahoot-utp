package com.adaptcode.utp.security;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import com.adaptcode.utp.model.RolUsuario;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

/**
 * Genera y valida los JWT de la aplicación.
 *
 * <p>Hay dos tipos de token, distinguidos por el claim {@code tipo}:</p>
 * <ul>
 *   <li><b>access</b>: vida corta (15 minutos). Es el que viaja en
 *       {@code Authorization: Bearer}.</li>
 *   <li><b>refresh</b>: vida larga (30 días), un solo uso y revocable en base
 *       de datos. Nunca se acepta como token de acceso.</li>
 * </ul>
 */
@Component
public class JwtService {

	public static final String TIPO_ACCESS = "access";
	public static final String TIPO_REFRESH = "refresh";

	private final Key key;
	private final long expirationMs;
	private final long refreshExpirationMs;
	private final String emitter;

	public JwtService(
			@Value("${JWT_SECRET:${jwt.secret}}") String secret,
			@Value("${jwt.expiration}") long expirationMs,
			@Value("${jwt.refresh-expiration:2592000000}") long refreshExpirationMs,
			@Value("${jwt.emitter:adaptcode-utp-api}") String emitter) {
		if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalStateException(
					"jwt.secret debe tener al menos 32 caracteres para HS256.");
		}
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.expirationMs = expirationMs;
		this.refreshExpirationMs = refreshExpirationMs;
		this.emitter = emitter;
	}

	public String generateToken(UsuarioPrincipal principal) {
		Instant ahora = Instant.now();
		return Jwts.builder()
				.setSubject(principal.getCorreo())
				.setIssuer(emitter)
				.setId(UUID.randomUUID().toString())
				.claim("id", principal.getId())
				.claim("rol", principal.getRol().name())
				.claim("tipo", TIPO_ACCESS)
				.setIssuedAt(Date.from(ahora))
				.setExpiration(Date.from(ahora.plusMillis(expirationMs)))
				.signWith(key, SignatureAlgorithm.HS256)
				.compact();
	}

	/**
	 * Genera un refresh token de un solo uso. El llamador debe guardar su hash:
	 * el valor en claro solo se entrega al cliente.
	 */
	public String generateRefreshToken(UsuarioPrincipal principal) {
		Instant ahora = Instant.now();
		return Jwts.builder()
				.setSubject(principal.getCorreo())
				.setIssuer(emitter)
				.setId(UUID.randomUUID().toString())
				.claim("id", principal.getId())
				.claim("rol", principal.getRol().name())
				.claim("tipo", TIPO_REFRESH)
				.setIssuedAt(Date.from(ahora))
				.setExpiration(Date.from(ahora.plusMillis(refreshExpirationMs)))
				.signWith(key, SignatureAlgorithm.HS256)
				.compact();
	}

	/**
	 * Valida firma, emisor, expiración y tipo de un refresh token. Lanza
	 * {@link JwtException} si no es un refresh token legítimo.
	 */
	public Claims parseRefreshToken(String token) {
		Claims claims = parseClaims(token);
		if (!TIPO_REFRESH.equals(claims.get("tipo", String.class))) {
			throw new JwtException("El token no es de refresco.");
		}
		return claims;
	}

	public long getExpirationMs() {
		return expirationMs;
	}

	public long getRefreshExpirationMs() {
		return refreshExpirationMs;
	}

	public String extractUsername(String token) {
		return parseClaims(token).getSubject();
	}

	public Long extractId(String token) {
		Object id = parseClaims(token).get("id");
		return id instanceof Number numero ? numero.longValue() : null;
	}

	public RolUsuario extractRol(String token) {
		String rol = parseClaims(token).get("rol", String.class);
		return rol == null ? null : RolUsuario.valueOf(rol);
	}

	public Instant extractIssuedAt(String token) {
		Date emitido = parseClaims(token).getIssuedAt();
		return emitido == null ? null : emitido.toInstant();
	}

	public Instant extractExpiration(String token) {
		Date expiracion = parseClaims(token).getExpiration();
		return expiracion == null ? null : expiracion.toInstant();
	}

	public boolean validateToken(String token, UserDetails userDetails) {
		try {
			if (!userDetails.isEnabled()) {
				return false;
			}
			Claims claims = parseClaims(token);
			// Un refresh token nunca debe servir para autenticar peticiones.
			if (TIPO_REFRESH.equals(claims.get("tipo", String.class))) {
				return false;
			}
			String correo = claims.getSubject();
			if (correo == null || !correo.equalsIgnoreCase(userDetails.getUsername())) {
				return false;
			}
			Date expiracion = claims.getExpiration();
			if (expiracion == null || expiracion.before(new Date())) {
				return false;
			}
			if (userDetails instanceof UsuarioPrincipal principal) {
				Instant cambio = principal.getCredencialCambiadaEn();
				if (cambio != null && claims.getIssuedAt() != null
						&& claims.getIssuedAt().toInstant().isBefore(cambio)) {
					return false;
				}
			}
			return true;
		} catch (JwtException | IllegalArgumentException ex) {
			return false;
		}
	}

	private Claims parseClaims(String token) {
		return Jwts.parserBuilder()
				.setSigningKey(key)
				.requireIssuer(emitter)
				.build()
				.parseClaimsJws(token)
				.getBody();
	}
}
