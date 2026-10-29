package com.adaptcode.utp.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.adaptcode.utp.model.AlternativaDocente;
import com.adaptcode.utp.model.Curso;
import com.adaptcode.utp.model.EstadoQuiz;
import com.adaptcode.utp.model.EstadoSala;
import com.adaptcode.utp.model.GeneroUsuario;
import com.adaptcode.utp.model.NivelMicroReto;
import com.adaptcode.utp.model.OrigenPregunta;
import com.adaptcode.utp.model.ParticipacionSala;
import com.adaptcode.utp.model.PreguntaDocente;
import com.adaptcode.utp.model.Quiz;
import com.adaptcode.utp.model.QuizItem;
import com.adaptcode.utp.model.RespuestaSala;
import com.adaptcode.utp.model.RolUsuario;
import com.adaptcode.utp.model.Sala;
import com.adaptcode.utp.model.Tema;
import com.adaptcode.utp.model.TipoMicroReto;
import com.adaptcode.utp.model.Usuario;
import com.adaptcode.utp.repository.AlternativaDocenteRepository;
import com.adaptcode.utp.repository.CursoRepository;
import com.adaptcode.utp.repository.ParticipacionSalaRepository;
import com.adaptcode.utp.repository.PreguntaDocenteRepository;
import com.adaptcode.utp.repository.QuizItemRepository;
import com.adaptcode.utp.repository.QuizRepository;
import com.adaptcode.utp.repository.RespuestaSalaRepository;
import com.adaptcode.utp.repository.SalaRepository;
import com.adaptcode.utp.repository.TemaRepository;
import com.adaptcode.utp.repository.UsuarioRepository;
import com.adaptcode.utp.security.JwtService;
import com.adaptcode.utp.security.UsuarioPrincipal;

/**
 * La partida de sala expone la pregunta y sus alternativas, pero nunca la
 * correcta. Solo el docente propietario y los estudiantes con participación
 * pueden leerla.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SalaJuegoIntegrationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private UsuarioRepository usuarioRepository;
	@Autowired
	private CursoRepository cursoRepository;
	@Autowired
	private TemaRepository temaRepository;
	@Autowired
	private PreguntaDocenteRepository preguntaDocenteRepository;
	@Autowired
	private AlternativaDocenteRepository alternativaDocenteRepository;
	@Autowired
	private QuizRepository quizRepository;
	@Autowired
	private QuizItemRepository quizItemRepository;
	@Autowired
	private SalaRepository salaRepository;
	@Autowired
	private ParticipacionSalaRepository participacionSalaRepository;
	@Autowired
	private RespuestaSalaRepository respuestaSalaRepository;
	@Autowired
	private JwtService jwtService;

	private Usuario docente;
	private Usuario estudiante;
	private Usuario intruso;
	private Sala sala;
	private AlternativaDocente correcta;
	private AlternativaDocente incorrecta;
	private ParticipacionSala participacion;

	@BeforeEach
	void preparar() {
		docente = crearUsuario("juego-docente@utp.edu.pe", "Docente",
				RolUsuario.DOCENTE_UTP);
		estudiante = crearUsuario("juego-estudiante@utp.edu.pe", "Estudiante",
				RolUsuario.ESTUDIANTE_UTP);
		intruso = crearUsuario("juego-intruso@utp.edu.pe", "Intruso",
				RolUsuario.ESTUDIANTE_UTP);
		Curso curso = cursoRepository.save(new Curso("Curso de juego"));
		Tema tema = temaRepository.save(new Tema(curso, "Tema de juego"));
		PreguntaDocente pregunta = preguntaDocenteRepository.save(
				new PreguntaDocente(docente, OrigenPregunta.PROPIA, tema,
						TipoMicroReto.SELECCION_MULTIPLE, NivelMicroReto.BASICO,
						"¿Cuánto vale 2 + 2?"));
		correcta = alternativaDocenteRepository.save(
				new AlternativaDocente(pregunta, "4", null, null, 1, true));
		incorrecta = alternativaDocenteRepository.save(
				new AlternativaDocente(pregunta, "5", null, null, 2, false));

		Quiz quiz = new Quiz(docente, "Quiz de juego");
		quiz.setEstado(EstadoQuiz.LISTO);
		quiz = quizRepository.save(quiz);
		quizItemRepository.save(new QuizItem(quiz, pregunta, 1));

		sala = new Sala(docente, quiz, "778899", 30);
		sala.setEstado(EstadoSala.EN_CURSO);
		sala.setPreguntaActual(pregunta);
		sala.setPreguntaIniciadaEn(Instant.now().minusSeconds(3));
		sala = salaRepository.save(sala);
		participacion = participacionSalaRepository
				.save(new ParticipacionSala(sala, estudiante));
	}

	@Test
	void elEstudianteVeLaPreguntaSinLaAlternativaCorrecta() throws Exception {
		mockMvc.perform(get("/api/salas/" + sala.getId() + "/pregunta-actual")
						.header("Authorization", "Bearer " + token(estudiante)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.preguntaId").exists())
				.andExpect(jsonPath("$.enunciado").value("¿Cuánto vale 2 + 2?"))
				.andExpect(jsonPath("$.numeroPregunta").value(1))
				.andExpect(jsonPath("$.totalPreguntas").value(1))
				.andExpect(jsonPath("$.alternativas.length()").value(2))
				.andExpect(jsonPath("$.alternativas[0].correcta").doesNotExist())
				.andExpect(jsonPath("$.correcta").doesNotExist())
				.andExpect(jsonPath("$.retroalimentacion").doesNotExist())
				.andExpect(jsonPath("$.respondida").value(false))
				.andExpect(jsonPath("$.milisegundosRestantes").isNumber());
	}

	@Test
	void trasResponderSeInformaSinRevelarElAcierto() throws Exception {
		RespuestaSala respuesta = new RespuestaSala(participacion,
				sala.getPreguntaActual(), true, false);
		respuesta.setAlternativaElegida(correcta);
		respuesta.setTiempoMs(1_500L);
		respuestaSalaRepository.save(respuesta);

		mockMvc.perform(get("/api/salas/" + sala.getId() + "/pregunta-actual")
						.header("Authorization", "Bearer " + token(estudiante)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.respondida").value(true))
				.andExpect(jsonPath("$.alternativaSeleccionadaId")
						.value(correcta.getId()))
				.andExpect(jsonPath("$.acierto").doesNotExist())
				.andExpect(jsonPath("$.alternativas[1].correcta").doesNotExist());
	}

	@Test
	void elDocentePropietarioTambienPuedeLeerla() throws Exception {
		mockMvc.perform(get("/api/salas/" + sala.getId() + "/pregunta-actual")
						.header("Authorization", "Bearer " + token(docente)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.alternativas.length()").value(2));
	}

	@Test
	void unEstudianteAjenoNoPuedeLeerla() throws Exception {
		mockMvc.perform(get("/api/salas/" + sala.getId() + "/pregunta-actual")
						.header("Authorization", "Bearer " + token(intruso)))
				.andExpect(status().isForbidden());
	}

	@Test
	void sinTokenNoSeExponeLaPregunta() throws Exception {
		mockMvc.perform(get("/api/salas/" + sala.getId() + "/pregunta-actual"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void sinPreguntaHabilitadaResponde404() throws Exception {
		Sala borrador = new Sala(docente, sala.getQuizOrigen(), "112233", 30);
		borrador = salaRepository.save(borrador);
		participacionSalaRepository.save(new ParticipacionSala(borrador, estudiante));

		mockMvc.perform(get("/api/salas/" + borrador.getId() + "/pregunta-actual")
						.header("Authorization", "Bearer " + token(estudiante)))
				.andExpect(status().isNotFound());
	}

	private Usuario crearUsuario(String correo, String nombres, RolUsuario rol) {
		return usuarioRepository.save(new Usuario(correo, nombres, "Prueba",
				"hash-no-usado", rol, true, GeneroUsuario.MASCULINO,
				LocalDate.of(2000, 1, 1)));
	}

	private String token(Usuario usuario) {
		return jwtService.generateToken(new UsuarioPrincipal(
				usuario.getId(), usuario.getCorreo(), usuario.getContrasenaHash(),
				usuario.getNombres(), usuario.getApellidos(), usuario.getRol(),
				usuario.getGenero(), usuario.getFechaNacimiento(),
				usuario.isActivo(), usuario.getCredencialCambiadaEn()));
	}
}
