package com.adaptcode.utp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.adaptcode.utp.model.GeneroUsuario;
import com.adaptcode.utp.model.RolUsuario;
import com.adaptcode.utp.security.UsuarioPrincipal;

class RateLimitFilterTest {

	private static final String IP = "10.20.30.40";

	@AfterEach
	void limpiarContexto() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void anonimo_seLimitaPorIp() throws Exception {
		RateLimitFilter filtro = new RateLimitFilter(true, false, 2, 100, 100, 100);

		MockHttpServletResponse primera = ejecutar(filtro, "/api/usuarios/1");
		MockHttpServletResponse segunda = ejecutar(filtro, "/api/usuarios/1");
		MockHttpServletResponse tercera = ejecutar(filtro, "/api/usuarios/1");

		assertEquals(200, primera.getStatus());
		assertEquals(200, segunda.getStatus());
		assertEquals(429, tercera.getStatus());
		assertNotNull(tercera.getHeader("Retry-After"));
		assertTrue(tercera.getContentAsString().contains("DEMASIADOS_INTENTOS"));
	}

	@Test
	void autenticados_deLaMismaIp_tienenCupoIndependiente() throws Exception {
		RateLimitFilter filtro = new RateLimitFilter(true, false, 100, 100, 100, 2);

		autenticar(1L);
		assertEquals(200, ejecutar(filtro, "/api/salas/1/ranking").getStatus());
		assertEquals(200, ejecutar(filtro, "/api/salas/1/ranking").getStatus());

		autenticar(2L);
		assertEquals(200, ejecutar(filtro, "/api/salas/1/ranking").getStatus());
		assertEquals(200, ejecutar(filtro, "/api/salas/1/ranking").getStatus());

		// Cada estudiante agotó su propio cupo, no el de la clase.
		assertEquals(429, ejecutar(filtro, "/api/salas/1/ranking").getStatus());
	}

	@Test
	void autenticacion_siempreSeLimitaPorIp() throws Exception {
		RateLimitFilter filtro = new RateLimitFilter(true, false, 100, 1, 100, 100);

		autenticar(1L);
		assertEquals(200, ejecutar(filtro, "/api/auth/login").getStatus());

		autenticar(2L);
		assertEquals(429, ejecutar(filtro, "/api/auth/login").getStatus());
	}

	private MockHttpServletResponse ejecutar(RateLimitFilter filtro, String ruta)
			throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", ruta);
		request.setRequestURI(ruta);
		request.setRemoteAddr(IP);
		MockHttpServletResponse response = new MockHttpServletResponse();
		filtro.doFilter(request, response, new MockFilterChain());
		return response;
	}

	private void autenticar(Long id) {
		UsuarioPrincipal principal = new UsuarioPrincipal(id,
				"usuario" + id + "@utp.edu.pe", "hash", "Usuario", "Prueba",
				RolUsuario.ESTUDIANTE_UTP, GeneroUsuario.MASCULINO,
				LocalDate.of(2000, 1, 1), true, null);
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(principal, null,
						principal.getAuthorities()));
	}
}
