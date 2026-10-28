package com.adaptcode.utp.integration;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.adaptcode.utp.model.GeneroUsuario;
import com.adaptcode.utp.model.RolUsuario;
import com.adaptcode.utp.model.Usuario;
import com.adaptcode.utp.repository.UsuarioRepository;
import com.adaptcode.utp.security.JwtService;
import com.adaptcode.utp.security.UsuarioPrincipal;

/**
 * Contrato de errores y trazabilidad. Cualquier 4xx/5xx debe compartir la misma
 * forma y devolver el {@code X-Request-Id} que aparece en los logs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ContratoErroresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private JwtService jwtService;

	@Test
	void sinToken_devuelve401ConContratoUnificadoYTraceId() throws Exception {
		mockMvc.perform(get("/api/docentes/1/quizzes"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().exists("X-Request-Id"))
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.code").value("NO_AUTENTICADO"))
				.andExpect(jsonPath("$.message").isNotEmpty())
				.andExpect(jsonPath("$.path").value("/api/docentes/1/quizzes"))
				.andExpect(jsonPath("$.traceId").isNotEmpty());
	}

	@Test
	void requestIdDelClienteSeRespetaYApareceEnElError() throws Exception {
		mockMvc.perform(get("/api/docentes/1/quizzes")
						.header("X-Request-Id", "prueba-12345678"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("X-Request-Id", "prueba-12345678"))
				.andExpect(jsonPath("$.traceId").value("prueba-12345678"));
	}

	@Test
	void validacion_devuelve400ConErroresPorCampo() throws Exception {
		String cuerpo = """
				{"correo": "no-es-un-correo", "contrasena": ""}
				""";
		mockMvc.perform(post("/api/auth/login")
						.contentType(APPLICATION_JSON).content(cuerpo))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDACION"))
				.andExpect(jsonPath("$.fieldErrors.correo").isNotEmpty())
				.andExpect(jsonPath("$.fieldErrors.contrasena").isNotEmpty());
	}

	@Test
	void accesoAUnaRutaDeOtroDocente_devuelve403ConElMismoContrato() throws Exception {
		Usuario docenteA = docente("c20261@utp.edu.pe");
		Usuario docenteB = docente("c20262@utp.edu.pe");
		String tokenA = token(docenteA);

		mockMvc.perform(get("/api/docentes/" + docenteB.getId() + "/quizzes")
						.header("Authorization", "Bearer " + tokenA))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403))
				.andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"))
				.andExpect(jsonPath("$.traceId").isNotEmpty());
	}

	private Usuario docente(String correo) {
		return usuarioRepository.save(new Usuario(correo, "Docente", "Prueba",
				"hash-no-usado", RolUsuario.DOCENTE_UTP, true,
				GeneroUsuario.MASCULINO, LocalDate.of(1985, 3, 20)));
	}

	private String token(Usuario usuario) {
		return jwtService.generateToken(new UsuarioPrincipal(
				usuario.getId(), usuario.getCorreo(), usuario.getContrasenaHash(),
				usuario.getNombres(), usuario.getApellidos(), usuario.getRol(),
				usuario.getGenero(), usuario.getFechaNacimiento(),
				usuario.isActivo(), usuario.getCredencialCambiadaEn()));
	}
}
