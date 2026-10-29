package com.adaptcode.utp.integration;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.adaptcode.utp.model.GeneroUsuario;
import com.adaptcode.utp.model.RolUsuario;
import com.adaptcode.utp.model.Usuario;
import com.adaptcode.utp.repository.UsuarioRepository;
import com.adaptcode.utp.security.JwtService;
import com.adaptcode.utp.security.UsuarioPrincipal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Portabilidad y supresión: solo el dueño accede a sus datos, la baja exige la
 * contraseña actual y deja la cuenta anonimizada e inactiva.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DatosPersonalesIntegrationTest {

	private static final String CORREO = "u2026777@utp.edu.pe";
	private static final String CONTRASENA = "Nube#2026Firme!";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtService jwtService;

	private Usuario usuario;
	private String token;

	@BeforeEach
	void preparar() {
		usuarioRepository.findByCorreoIgnoreCase(CORREO)
				.ifPresent(usuarioRepository::delete);
		usuario = usuarioRepository.save(new Usuario(CORREO, "Ana", "Torres",
				passwordEncoder.encode(CONTRASENA), RolUsuario.ESTUDIANTE_UTP,
				true, GeneroUsuario.FEMENINO, LocalDate.of(2004, 5, 10)));
		token = jwtService.generateToken(new UsuarioPrincipal(
				usuario.getId(), usuario.getCorreo(), usuario.getContrasenaHash(),
				usuario.getNombres(), usuario.getApellidos(), usuario.getRol(),
				usuario.getGenero(), usuario.getFechaNacimiento(),
				usuario.isActivo(), usuario.getCredencialCambiadaEn()));
	}

	@Test
	void elUsuarioExportaSusPropiosDatos() throws Exception {
		mockMvc.perform(get("/api/usuarios/" + usuario.getId() + "/datos")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.correo").value(CORREO))
				.andExpect(jsonPath("$.nombres").value("Ana"))
				.andExpect(jsonPath("$.participaciones").isArray())
				.andExpect(jsonPath("$.respuestas").isArray());
	}

	@Test
	void noSePuedenExportarDatosAjenos() throws Exception {
		Usuario otro = usuarioRepository.save(new Usuario("u2026778@utp.edu.pe",
				"Otro", "Usuario", "hash-no-usado", RolUsuario.ESTUDIANTE_UTP,
				true, GeneroUsuario.MASCULINO, LocalDate.of(2004, 6, 11)));

		mockMvc.perform(get("/api/usuarios/" + otro.getId() + "/datos")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));
	}

	@Test
	void laBajaExigeLaContrasenaActual() throws Exception {
		mockMvc.perform(post("/api/usuarios/" + usuario.getId() + "/eliminar")
						.header("Authorization", "Bearer " + token)
						.contentType(APPLICATION_JSON)
						.content("{\"contrasena\": \"Incorrecta#2026\"}"))
				.andExpect(status().isUnauthorized());
		assertTrue(usuario.isActivo());
	}

	@Test
	void laBajaAnonimizaYDesactivaLaCuenta() throws Exception {
		mockMvc.perform(post("/api/usuarios/" + usuario.getId() + "/eliminar")
						.header("Authorization", "Bearer " + token)
						.contentType(APPLICATION_JSON)
						.content("{\"contrasena\": \"" + CONTRASENA + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mensaje").isNotEmpty());

		Usuario actualizado = usuarioRepository.findById(usuario.getId()).orElseThrow();
		assertFalse(actualizado.isActivo());
		assertEquals("eliminado-" + usuario.getId() + "@anonimo.invalid",
				actualizado.getCorreo());
		assertEquals("Usuario", actualizado.getNombres());
		assertEquals("Eliminado", actualizado.getApellidos());
		assertNull(actualizado.getGenero());
		assertNull(actualizado.getFechaNacimiento());
	}
}
