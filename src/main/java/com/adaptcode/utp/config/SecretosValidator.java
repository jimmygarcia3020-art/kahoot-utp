package com.adaptcode.utp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

/**
 * Con {@code prod} activo, la aplicación se niega a arrancar si falta un secreto
 * o si sigue activo un valor pensado para demo. Es preferible fallar al desplegar
 * que servir tokens firmados con una clave publicada en el repositorio.
 */
@Component
@Profile("prod")
@RequiredArgsConstructor
public class SecretosValidator {

	private static final String JWT_DEMO = "dev-only-demo-jwt-secret-change-me-2026";
	private static final String PEPPER_DEMO = "dev-only-codigo-pepper-change-me-2026";

	private final Environment environment;

	@Value("${seguridad.exponer-codigos:false}")
	private boolean exponerCodigos;

	@PostConstruct
	public void validar() {
		exigir("JWT_SECRET", 32, JWT_DEMO,
				"define JWT_SECRET con al menos 32 caracteres aleatorios");
		exigir("CODIGO_PEPPER", 16, PEPPER_DEMO,
				"define CODIGO_PEPPER con al menos 16 caracteres aleatorios");
		exigir("ADAPTCODE_DB_PASSWORD", 8, null,
				"define ADAPTCODE_DB_PASSWORD; no se aceptan credenciales por defecto");
		if (exponerCodigos) {
			throw new IllegalStateException(
					"seguridad.exponer-codigos debe ser false en prod.");
		}
		String url = environment.getProperty("spring.datasource.url", "");
		if (url.isBlank()) {
			throw new IllegalStateException(
					"ADAPTCODE_DB_URL es obligatorio en prod.");
		}
		String mailHost = environment.getProperty("spring.mail.host", "");
		if (mailHost.isBlank()) {
			throw new IllegalStateException(
					"ADAPTCODE_MAIL_HOST es obligatorio en prod: los códigos "
							+ "deben enviarse por correo.");
		}
	}

	private void exigir(String variable, int longitudMinima, String valorDemo,
			String ayuda) {
		String valor = environment.getProperty(variable);
		if (valor == null || valor.isBlank()
				|| valor.length() < longitudMinima
				|| valor.equals(valorDemo)) {
			throw new IllegalStateException(
					"Configuracion insegura: " + ayuda + ".");
		}
	}
}
