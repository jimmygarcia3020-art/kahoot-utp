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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.adaptcode.utp.model.AlternativaDocente;
import com.adaptcode.utp.model.Curso;
import com.adaptcode.utp.model.EstadoQuiz;
import com.adaptcode.utp.model.GeneroUsuario;
import com.adaptcode.utp.model.NivelMicroReto;
import com.adaptcode.utp.model.OrigenPregunta;
import com.adaptcode.utp.model.PreguntaDocente;
import com.adaptcode.utp.model.Quiz;
import com.adaptcode.utp.model.QuizItem;
import com.adaptcode.utp.model.RolUsuario;
import com.adaptcode.utp.model.Tema;
import com.adaptcode.utp.model.TipoMicroReto;
import com.adaptcode.utp.model.Usuario;
import com.adaptcode.utp.repository.AlternativaDocenteRepository;
import com.adaptcode.utp.repository.CursoRepository;
import com.adaptcode.utp.repository.PreguntaDocenteRepository;
import com.adaptcode.utp.repository.QuizItemRepository;
import com.adaptcode.utp.repository.QuizRepository;
import com.adaptcode.utp.repository.TemaRepository;
import com.adaptcode.utp.repository.UsuarioRepository;
import com.adaptcode.utp.security.JwtService;
import com.adaptcode.utp.security.UsuarioPrincipal;
import com.jayway.jsonpath.JsonPath;

/**
 * Recorrido HTTP completo de una sala: crear, abrir la espera, unirse con PIN,
 * iniciar, responder, avanzar de pregunta, consultar ranking y finalizar.
 *
 * <p>Comprueba dos reglas del contrato: el acierto no viaja al cliente mientras
 * la pregunta siga abierta, y el ranking no incluye la ronda en curso (de lo
 * contrario los puntos revelarían la alternativa correcta antes de tiempo).</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SalaFlujoIntegrationTest {

	private static final String AUTORIZACION = "Authorization";

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
	private JwtService jwtService;

	private Usuario docente;
	private Usuario estudiante;
	private Usuario intruso;
	private Quiz quiz;
	private PreguntaDocente primera;
	private PreguntaDocente segunda;
	private AlternativaDocente correctaPrimera;
	private AlternativaDocente incorrectaSegunda;

	@BeforeEach
	void preparar() {
		docente = crearUsuario("flujo-docente@utp.edu.pe", "Docente",
				RolUsuario.DOCENTE_UTP);
		estudiante = crearUsuario("flujo-estudiante@utp.edu.pe", "Estudiante",
				RolUsuario.ESTUDIANTE_UTP);
		intruso = crearUsuario("flujo-intruso@utp.edu.pe", "Intruso",
				RolUsuario.ESTUDIANTE_UTP);
		Curso curso = cursoRepository.save(new Curso("Curso de flujo"));
		Tema tema = temaRepository.save(new Tema(curso, "Tema de flujo"));
		primera = crearPregunta(tema, "¿Cuánto vale 2 + 2?");
		segunda = crearPregunta(tema, "¿Cuánto vale 3 + 3?");
		correctaPrimera = alternativaDocenteRepository.save(
				new AlternativaDocente(primera, "4", null, null, 1, true));
		alternativaDocenteRepository.save(
				new AlternativaDocente(primera, "5", null, null, 2, false));
		alternativaDocenteRepository.save(
				new AlternativaDocente(segunda, "6", null, null, 1, true));
		incorrectaSegunda = alternativaDocenteRepository.save(
				new AlternativaDocente(segunda, "7", null, null, 2, false));

		quiz = new Quiz(docente, "Quiz de flujo");
		quiz.setEstado(EstadoQuiz.LISTO);
		quiz = quizRepository.save(quiz);
		quizItemRepository.save(new QuizItem(quiz, primera, 1));
		quizItemRepository.save(new QuizItem(quiz, segunda, 2));
	}

	@Test
	void flujoCompleto_ocultaAciertoYExcluyeLaRondaAbiertaDelRanking()
			throws Exception {
		String tokenDocente = token(docente);
		String tokenEstudiante = token(estudiante);

		String creada = ejecutar(post(
				"/api/docentes/" + docente.getId() + "/salas"), tokenDocente,
				"{\"quizId\":" + quiz.getId()
						+ ",\"tiempoPorPregunta\":30,\"ampliacion\":\"ESTANDAR\"}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.estado").value("BORRADOR"))
				.andReturn().getResponse().getContentAsString();
		long salaId = numero(creada, "$.id");
		String pin = JsonPath.read(creada, "$.pin");

		ejecutar(post("/api/docentes/" + docente.getId() + "/salas/" + salaId
				+ "/abrir-espera"), tokenDocente, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("SALA_ESPERA"));

		ejecutar(post("/api/salas/" + salaId + "/participaciones"),
				token(intruso), "{\"estudianteId\":" + intruso.getId()
						+ ",\"pin\":\"000000\"}")
				.andExpect(status().isForbidden());

		String participacion = ejecutar(post(
				"/api/salas/" + salaId + "/participaciones"), tokenEstudiante,
				"{\"estudianteId\":" + estudiante.getId()
						+ ",\"pin\":\"" + pin + "\"}")
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long participacionId = numero(participacion, "$.id");

		ejecutar(post("/api/docentes/" + docente.getId() + "/salas/" + salaId
				+ "/iniciar"), tokenDocente, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("EN_CURSO"))
				.andExpect(jsonPath("$.preguntaActualId")
						.value(primera.getId()));

		ejecutar(get("/api/salas/" + salaId + "/pregunta-actual"),
				tokenEstudiante, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.preguntaId").value(primera.getId()))
				.andExpect(jsonPath("$.alternativas.length()").value(2))
				.andExpect(jsonPath("$.alternativas[0].correcta")
						.doesNotExist());

		String respuestaPrimera = ejecutar(post(
				"/api/salas/" + salaId + "/respuestas"), tokenEstudiante,
				"{\"participacionId\":" + participacionId
						+ ",\"preguntaId\":" + primera.getId()
						+ ",\"alternativaId\":" + correctaPrimera.getId()
						+ ",\"omitida\":false}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.acierto").doesNotExist())
				.andReturn().getResponse().getContentAsString();
		long respuestaId = numero(respuestaPrimera, "$.id");

		// El envío es definitivo y no se puede repetir.
		ejecutar(post("/api/salas/" + salaId + "/respuestas"),
				tokenEstudiante,
				"{\"participacionId\":" + participacionId
						+ ",\"preguntaId\":" + primera.getId()
						+ ",\"alternativaId\":" + correctaPrimera.getId()
						+ ",\"omitida\":false}")
				.andExpect(status().isBadRequest());

		ejecutar(get("/api/salas/" + salaId + "/respuestas/" + respuestaId),
				tokenEstudiante, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.acierto").doesNotExist());

		// La ronda sigue abierta: el acierto no suma puntos todavía.
		ejecutar(get("/api/salas/" + salaId + "/ranking"), tokenDocente, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].puntos").value(0));

		ejecutar(post("/api/docentes/" + docente.getId() + "/salas/" + salaId
				+ "/siguiente-pregunta"), tokenDocente, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.preguntaActualId")
						.value(segunda.getId()));

		// Al cerrar la ronda, el acierto se revela y cuenta para el ranking.
		ejecutar(get("/api/salas/" + salaId + "/respuestas/" + respuestaId),
				tokenEstudiante, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.acierto").value(true));
		ejecutar(get("/api/salas/" + salaId + "/ranking"), tokenDocente, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].puntos").value(1));

		String respuestaSegunda = ejecutar(post(
				"/api/salas/" + salaId + "/respuestas"), tokenEstudiante,
				"{\"participacionId\":" + participacionId
						+ ",\"preguntaId\":" + segunda.getId()
						+ ",\"alternativaId\":" + incorrectaSegunda.getId()
						+ ",\"omitida\":false}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.acierto").doesNotExist())
				.andReturn().getResponse().getContentAsString();
		long respuestaSegundaId = numero(respuestaSegunda, "$.id");

		// La segunda ronda tampoco cuenta mientras está abierta.
		ejecutar(get("/api/salas/" + salaId + "/ranking"), tokenDocente, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].puntos").value(1));

		ejecutar(post("/api/docentes/" + docente.getId() + "/salas/" + salaId
				+ "/finalizar"), tokenDocente, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("FINALIZADA"));

		ejecutar(get("/api/salas/" + salaId + "/respuestas/"
				+ respuestaSegundaId), tokenEstudiante, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.acierto").value(false));
		ejecutar(get("/api/salas/" + salaId + "/pregunta-actual"),
				tokenEstudiante, null)
				.andExpect(status().isNotFound());
	}

	@Test
	void sinPinCorrectoNoSePuedeEntrarAunqueLaSalaEsteEnEspera() throws Exception {
		String tokenDocente = token(docente);
		String creada = ejecutar(post(
				"/api/docentes/" + docente.getId() + "/salas"), tokenDocente,
				"{\"quizId\":" + quiz.getId()
						+ ",\"tiempoPorPregunta\":30,\"ampliacion\":\"ESTANDAR\"}")
				.andReturn().getResponse().getContentAsString();
		long salaId = numero(creada, "$.id");
		String pinCorrecto = JsonPath.read(creada, "$.pin");

		ejecutar(post("/api/docentes/" + docente.getId() + "/salas/" + salaId
				+ "/abrir-espera"), tokenDocente, null)
				.andExpect(status().isOk());

		String pinIncorrecto = "000000".equals(pinCorrecto) ? "111111"
				: "000000";
		ejecutar(post("/api/salas/" + salaId + "/participaciones"),
				token(estudiante),
				"{\"estudianteId\":" + estudiante.getId()
						+ ",\"pin\":\"" + pinIncorrecto + "\"}")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));
	}

	private PreguntaDocente crearPregunta(Tema tema, String enunciado) {
		return preguntaDocenteRepository.save(new PreguntaDocente(docente,
				OrigenPregunta.PROPIA, tema, TipoMicroReto.SELECCION_MULTIPLE,
				NivelMicroReto.BASICO, enunciado));
	}

	private Usuario crearUsuario(String correo, String nombres, RolUsuario rol) {
		return usuarioRepository.save(new Usuario(correo, nombres, "Prueba",
				"hash-no-usado", rol, true, GeneroUsuario.MASCULINO,
				LocalDate.of(2000, 1, 1)));
	}

	private org.springframework.test.web.servlet.ResultActions ejecutar(
			MockHttpServletRequestBuilder builder, String token, String cuerpo)
			throws Exception {
		if (token != null) {
			builder.header(AUTORIZACION, "Bearer " + token);
		}
		if (cuerpo != null) {
			builder.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
		}
		return mockMvc.perform(builder);
	}

	private long numero(String json, String expresion) {
		Number valor = JsonPath.read(json, expresion);
		return valor.longValue();
	}

	private String token(Usuario usuario) {
		return jwtService.generateToken(new UsuarioPrincipal(
				usuario.getId(), usuario.getCorreo(), usuario.getContrasenaHash(),
				usuario.getNombres(), usuario.getApellidos(), usuario.getRol(),
				usuario.getGenero(), usuario.getFechaNacimiento(),
				usuario.isActivo(), usuario.getCredencialCambiadaEn()));
	}
}
