package com.adaptcode.utp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Documentación OpenAPI de la API. Disponible en {@code /v3/api-docs} y con
 * interfaz interactiva en {@code /swagger-ui.html}.
 *
 * <p>El esquema de seguridad es Bearer JWT: en Swagger UI hay que usar
 * <em>Authorize</em> con el access token (no el refresh token) para probar los
 * endpoints protegidos. Los endpoints públicos de autenticación y catálogo no
 * necesitan token.</p>
 */
@Configuration
public class OpenApiConfig {

	private static final String ESQUEMA = "bearerAuth";

	@Bean
	public OpenAPI adaptcodeOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("AdaptCode UTP API")
						.version("1.0.0")
						.description("""
								API REST de la plataforma educativa AdaptCode UTP.

								Autenticación: obtenga un access token con POST /api/auth/login
								y envíelo como `Authorization: Bearer <token>`.
								El refresh token solo sirve en POST /api/auth/refresh y
								POST /api/auth/logout; nunca como token de acceso.

								Los errores siguen un contrato único:
								`timestamp`, `status`, `code`, `message`, `path`, `traceId`
								y `fieldErrors`. El `traceId` coincide con `X-Request-Id`.

								Por seguridad nunca se expone la alternativa correcta, el
								orden esperado ni la retroalimentación de evaluación.
								""")
						.contact(new Contact().name("AdaptCode UTP")
								.email("soporte@adaptcode.utp.edu.pe"))
						.license(new License().name("Uso académico")))
				.components(new Components().addSecuritySchemes(ESQUEMA,
						new SecurityScheme()
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")
								.description("Access token de 15 minutos.")))
				.addSecurityItem(new SecurityRequirement().addList(ESQUEMA));
	}
}
