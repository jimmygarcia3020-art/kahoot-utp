package com.adaptcode.utp.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.adaptcode.utp.dto.request.LoginRequest;
import com.adaptcode.utp.dto.request.RefreshTokenRequest;
import com.adaptcode.utp.dto.response.AuthResponse;
import com.adaptcode.utp.exception.AutenticacionException;
import com.adaptcode.utp.model.GeneroUsuario;
import com.adaptcode.utp.model.RolUsuario;
import com.adaptcode.utp.model.Usuario;
import com.adaptcode.utp.repository.TokenRefrescoRepository;
import com.adaptcode.utp.repository.UsuarioRepository;
import com.adaptcode.utp.service.AuthService;

/**
 * Ciclo de vida del refresh token: emisión en login, rotación en cada refresh,
 * detección de reutilización y revocación en logout. Un refresh token nunca
 * debe servir como access token.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefreshTokenIntegrationTest {

	private static final String CORREO = "u20261234@utp.edu.pe";
	private static final String CONTRASENA = "Nube#2026Firme!";

	@Autowired
	private AuthService authService;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TokenRefrescoRepository tokenRefrescoRepository;

	@BeforeEach
	void preparar() {
		usuarioRepository.findByCorreoIgnoreCase(CORREO)
				.ifPresent(usuarioRepository::delete);
		usuarioRepository.save(new Usuario(CORREO, "Ana", "Torres",
				passwordEncoder.encode(CONTRASENA), RolUsuario.ESTUDIANTE_UTP,
				true, GeneroUsuario.FEMENINO, LocalDate.of(2004, 5, 10)));
	}

	@AfterEach
	void limpiar() {
		// La FK tokens_refresco -> usuarios es ON DELETE CASCADE.
		usuarioRepository.findByCorreoIgnoreCase(CORREO)
				.ifPresent(usuarioRepository::delete);
	}

	@Test
	void elRefreshRotaElTokenYElAnteriorYaNoSirve() {
		AuthResponse login = authService.login(new LoginRequest(CORREO, CONTRASENA));
		assertNotNull(login.refreshToken());
		assertTrue(login.refreshExpiraEnMs() > login.expiraEnMs());

		AuthResponse renovado = authService
				.refrescar(new RefreshTokenRequest(login.refreshToken()));

		assertNotNull(renovado.token());
		assertNotEquals(login.refreshToken(), renovado.refreshToken());

		assertThrows(AutenticacionException.class, () -> authService
				.refrescar(new RefreshTokenRequest(login.refreshToken())));
	}

	@Test
	void reutilizarUnTokenRevocadoCierraTodasLasSesiones() {
		AuthResponse login = authService.login(new LoginRequest(CORREO, CONTRASENA));
		AuthResponse renovado = authService
				.refrescar(new RefreshTokenRequest(login.refreshToken()));

		// Reutilización del token ya rotado: se revoca toda la cadena.
		assertThrows(AutenticacionException.class, () -> authService
				.refrescar(new RefreshTokenRequest(login.refreshToken())));
		assertThrows(AutenticacionException.class, () -> authService
				.refrescar(new RefreshTokenRequest(renovado.refreshToken())));
	}

	@Test
	void logoutRevocaElTokenYNoSePuedeVolverARenovar() {
		AuthResponse login = authService.login(new LoginRequest(CORREO, CONTRASENA));
		authService.cerrarSesion(new RefreshTokenRequest(login.refreshToken()));

		assertThrows(AutenticacionException.class, () -> authService
				.refrescar(new RefreshTokenRequest(login.refreshToken())));
	}

	@Test
	void elRefreshTokenNoAutenticaPeticiones() throws Exception {
		AuthResponse login = authService.login(new LoginRequest(CORREO, CONTRASENA));

		mockMvc.perform(get("/api/docentes/1/quizzes")
						.header("Authorization", "Bearer " + login.refreshToken()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void noAcumulaMasSesionesActivasQueElMaximo() {
		ReflectionTestUtils.setField(authService, "maxSesionesActivas", 2);
		try {
			AuthResponse primera = authService
					.login(new LoginRequest(CORREO, CONTRASENA));
			authService.login(new LoginRequest(CORREO, CONTRASENA));
			AuthResponse tercera = authService
					.login(new LoginRequest(CORREO, CONTRASENA));
			Long usuarioId = usuarioRepository.findByCorreoIgnoreCase(CORREO)
					.orElseThrow().getId();

			// La sesión más antigua se revoca al superar el tope.
			assertEquals(2, tokenRefrescoRepository
					.findAllByUsuario_IdAndRevocadoFalseOrderByCreadoEnAscIdAsc(
							usuarioId)
					.size());
			// La más reciente sigue siendo válida.
			assertNotNull(authService
					.refrescar(new RefreshTokenRequest(tercera.refreshToken())));
			// Reutilizar la revocada se detecta como robo y cierra la cadena.
			assertThrows(AutenticacionException.class, () -> authService
					.refrescar(new RefreshTokenRequest(primera.refreshToken())));
			assertEquals(0, tokenRefrescoRepository
					.findAllByUsuario_IdAndRevocadoFalseOrderByCreadoEnAscIdAsc(
							usuarioId)
					.size());
		} finally {
			ReflectionTestUtils.setField(authService, "maxSesionesActivas", 100);
		}
	}

	@Test
	void dosRefrescosSimultaneosSoloRotanUnaVez() throws Exception {
		AuthResponse login = authService.login(new LoginRequest(CORREO, CONTRASENA));
		ExecutorService ejecutor = Executors.newFixedThreadPool(2);
		CountDownLatch listos = new CountDownLatch(2);
		CountDownLatch salida = new CountDownLatch(1);
		try {
			java.util.concurrent.Callable<Object> intento = () -> {
				listos.countDown();
				salida.await(5, TimeUnit.SECONDS);
				try {
					return authService.refrescar(
							new RefreshTokenRequest(login.refreshToken()));
				} catch (RuntimeException ex) {
					return ex;
				}
			};
			Future<Object> primero = ejecutor.submit(intento);
			Future<Object> segundo = ejecutor.submit(intento);
			listos.await(5, TimeUnit.SECONDS);
			salida.countDown();
			List<Object> resultados = List.of(primero.get(10, TimeUnit.SECONDS),
					segundo.get(10, TimeUnit.SECONDS));
			long exitos = resultados.stream()
					.filter(resultado -> resultado instanceof AuthResponse)
					.count();
			assertEquals(1, exitos,
					"Solo una petición concurrente debe rotar el token.");
			assertEquals(1, resultados.stream()
					.filter(resultado -> resultado instanceof AutenticacionException)
					.count());
		} finally {
			ejecutor.shutdownNow();
		}
	}
}
