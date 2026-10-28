package com.adaptcode.utp.integration;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.adaptcode.utp.model.GeneroUsuario;
import com.adaptcode.utp.model.Quiz;
import com.adaptcode.utp.model.RolUsuario;
import com.adaptcode.utp.model.Sala;
import com.adaptcode.utp.model.Usuario;
import com.adaptcode.utp.repository.QuizRepository;
import com.adaptcode.utp.repository.SalaRepository;
import com.adaptcode.utp.repository.UsuarioRepository;
import com.adaptcode.utp.security.JwtService;
import com.adaptcode.utp.security.UsuarioPrincipal;

/**
 * Acceso al flujo SSE: el ticket solo lo obtiene quien puede leer la sala y el
 * flujo nunca acepta el JWT por URL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SalaEventoIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private QuizRepository quizRepository;

	@Autowired
	private SalaRepository salaRepository;

	@Autowired
	private JwtService jwtService;

	private Usuario docente;
	private Sala sala;

	@BeforeEach
	void preparar() {
		docente = usuarioRepository.save(new Usuario("c2026888@utp.edu.pe",
				"Docente", "Eventos", "hash-no-usado", RolUsuario.DOCENTE_UTP,
				true, GeneroUsuario.MASCULINO, LocalDate.of(1985, 3, 20)));
		Quiz quiz = quizRepository.save(new Quiz(docente, "Quiz de eventos"));
		sala = salaRepository.save(new Sala(docente, quiz, "445566", 30));
	}

	@Test
	void sinTokenNoSeEmiteTicket() throws Exception {
		mockMvc.perform(post("/api/salas/" + sala.getId() + "/eventos/ticket"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("NO_AUTENTICADO"));
	}

	@Test
	void unTicketInventadoNoAbreElFlujo() throws Exception {
		mockMvc.perform(get("/api/salas/" + sala.getId() + "/eventos")
						.param("ticket", "inventado"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("NO_AUTENTICADO"))
				.andExpect(jsonPath("$.traceId").isNotEmpty());
	}

	@Test
	void sinTicketLaPeticionEsInvalida() throws Exception {
		mockMvc.perform(get("/api/salas/" + sala.getId() + "/eventos"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SOLICITUD_INVALIDA"));
	}

	@Test
	void elDocentePropietarioObtieneUnTicket() throws Exception {
		mockMvc.perform(post("/api/salas/" + sala.getId() + "/eventos/ticket")
						.header("Authorization", "Bearer " + token(docente)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ticket").isNotEmpty())
				.andExpect(jsonPath("$.vigenciaMs").value(60000));
	}

	private String token(Usuario usuario) {
		return jwtService.generateToken(new UsuarioPrincipal(
				usuario.getId(), usuario.getCorreo(), usuario.getContrasenaHash(),
				usuario.getNombres(), usuario.getApellidos(), usuario.getRol(),
				usuario.getGenero(), usuario.getFechaNacimiento(),
				usuario.isActivo(), usuario.getCredencialCambiadaEn()));
	}
}
