package com.adaptcode.utp.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Límite de intentos de inicio de sesión por correo. Tras N fallos la cuenta
 * queda bloqueada temporalmente. El estado es en memoria: en un despliegue con
 * varias instancias debe reemplazarse por un almacén compartido (Redis, etc.).
 */
@Component
public class LoginAttemptService {

	private final int maxIntentos;
	private final long bloqueoMinutos;
	private final Map<String, Registro> registros = new ConcurrentHashMap<>();

	public LoginAttemptService(
			@Value("${seguridad.login-max-intentos:5}") int maxIntentos,
			@Value("${seguridad.login-bloqueo-minutos:15}") long bloqueoMinutos) {
		this.maxIntentos = Math.max(1, maxIntentos);
		this.bloqueoMinutos = Math.max(1, bloqueoMinutos);
	}

	public boolean estaBloqueado(String correo) {
		return segundosRestantes(correo) > 0;
	}

	public long segundosRestantes(String correo) {
		String clave = clave(correo);
		Registro registro = registros.get(clave);
		if (registro == null || registro.bloqueadoHasta == null) {
			return 0;
		}
		long segundos = Duration.between(Instant.now(), registro.bloqueadoHasta)
				.toSeconds();
		if (segundos <= 0) {
			registros.remove(clave);
			return 0;
		}
		return segundos;
	}

	public void registrarFallo(String correo) {
		String clave = clave(correo);
		registros.compute(clave, (ignorada, actual) -> {
			Registro registro = actual == null ? new Registro() : actual;
			if (registro.bloqueadoHasta != null
					&& registro.bloqueadoHasta.isAfter(Instant.now())) {
				return registro;
			}
			registro.intentos++;
			registro.ultimoFallo = Instant.now();
			if (registro.intentos >= maxIntentos) {
				registro.bloqueadoHasta = Instant.now()
						.plus(Duration.ofMinutes(bloqueoMinutos));
			}
			return registro;
		});
		if (registros.size() > 10_000) {
			limpiarExpirados();
		}
	}

	public void registrarExito(String correo) {
		registros.remove(clave(correo));
	}

	private void limpiarExpirados() {
		Instant ahora = Instant.now();
		Instant limiteFallos = ahora.minus(Duration.ofMinutes(bloqueoMinutos));
		registros.entrySet().removeIf(entrada -> {
			Registro registro = entrada.getValue();
			if (registro.bloqueadoHasta != null) {
				return registro.bloqueadoHasta.isBefore(ahora);
			}
			// Tambien se descartan los correos con fallos viejos que nunca
			// llegaron al bloqueo; antes quedaban en memoria para siempre.
			return registro.ultimoFallo == null
					|| registro.ultimoFallo.isBefore(limiteFallos);
		});
	}

	private String clave(String correo) {
		return correo == null ? "" : correo.trim().toLowerCase(Locale.ROOT);
	}

	private static final class Registro {
		private int intentos;
		private Instant ultimoFallo;
		private Instant bloqueadoHasta;
	}
}
