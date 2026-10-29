package com.adaptcode.utp.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.adaptcode.utp.model.CodigoVerificacion;
import com.adaptcode.utp.model.TipoCodigoVerificacion;
import com.adaptcode.utp.repository.CodigoVerificacionRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * Flujo real de registro, verificación y login contra el contexto completo.
 * Comprueba además que el código nunca se persiste en claro.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthFlowIntegrationTest {

	private static final String CONTRASENA = "Nube#2026Firme!";
	private static final String CORREO = "u20260001@utp.edu.pe";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CodigoVerificacionRepository codigoVerificacionRepository;

	@Test
	void registroVerificacionYLogin() throws Exception {
		String registro = """
				{
				  "nombres": "Ana",
				  "apellidos": "Torres",
				  "correo": "%s",
				  "contrasena": "%s",
				  "genero": "FEMENINO",
				  "fechaNacimiento": "2004-05-10"
				}
				""".formatted(CORREO, CONTRASENA);

		String respuesta = mockMvc.perform(post("/api/auth/registro")
						.contentType(APPLICATION_JSON).content(registro))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.correo").value(CORREO))
				.andExpect(jsonPath("$.rol").value("ESTUDIANTE_UTP"))
				.andExpect(jsonPath("$.codigoVerificacion").isNotEmpty())
				.andReturn().getResponse().getContentAsString();

		String codigo = JsonPath.read(respuesta, "$.codigoVerificacion");
		assertTrue(codigo.matches("\\d{6}"));

		CodigoVerificacion guardado = codigoVerificacionRepository
				.findTopByCorreoAndTipoOrderByCreadoEnDesc(CORREO,
						TipoCodigoVerificacion.VERIFICACION_CUENTA)
				.orElseThrow();
		assertNotEquals(codigo, guardado.getCodigo(),
				"El código no debe guardarse en claro.");
		assertEquals(64, guardado.getCodigo().length(),
				"El código debe guardarse como HMAC-SHA256 hexadecimal.");

		String verificacion = """
				{"correo": "%s", "codigo": "%s"}
				""".formatted(CORREO, codigo);
		mockMvc.perform(post("/api/auth/verificar-cuenta")
						.contentType(APPLICATION_JSON).content(verificacion))
				.andExpect(status().isOk());

		String login = """
				{"correo": "%s", "contrasena": "%s"}
				""".formatted(CORREO, CONTRASENA);
		mockMvc.perform(post("/api/auth/login")
						.contentType(APPLICATION_JSON).content(login))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty())
				.andExpect(jsonPath("$.correo").value(CORREO));
	}

	@Test
	void codigoIncorrectoNoRevelaSiLaCuentaExiste() throws Exception {
		String cuerpo = """
				{"correo": "nadie@ejemplo.com", "codigo": "000000"}
				""";
		mockMvc.perform(post("/api/auth/verificar-cuenta")
						.contentType(APPLICATION_JSON).content(cuerpo))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(
						"El código no es válido o ya expiró."));
	}

	@Test
	void cuentaYaVerificada_noConfirmaSuEstadoSinValidarElCodigo() throws Exception {
		String correo = "u20260002@utp.edu.pe";
		String registro = """
				{
				  "nombres": "Luis",
				  "apellidos": "Ramos",
				  "correo": "%s",
				  "contrasena": "%s",
				  "genero": "MASCULINO",
				  "fechaNacimiento": "2003-11-02"
				}
				""".formatted(correo, CONTRASENA);
		String respuesta = mockMvc.perform(post("/api/auth/registro")
						.contentType(APPLICATION_JSON).content(registro))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String codigo = JsonPath.read(respuesta, "$.codigoVerificacion");

		String verificacion = """
				{"correo": "%s", "codigo": "%s"}
				""".formatted(correo, codigo);
		mockMvc.perform(post("/api/auth/verificar-cuenta")
						.contentType(APPLICATION_JSON).content(verificacion))
				.andExpect(status().isOk());

		// La cuenta ya está activa, pero un código inválido nunca debe recibir
		// la respuesta "ya está verificada": eso confirmaría el estado de la
		// cuenta a quien no controla el correo.
		String invalido = """
				{"correo": "%s", "codigo": "000000"}
				""".formatted(correo);
		mockMvc.perform(post("/api/auth/verificar-cuenta")
						.contentType(APPLICATION_JSON).content(invalido))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(
						"El código no es válido o ya expiró."));
	}
}
