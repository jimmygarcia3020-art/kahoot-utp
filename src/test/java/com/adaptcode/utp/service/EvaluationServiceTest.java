package com.adaptcode.utp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.adaptcode.utp.dto.request.EvaluarIntentoRequest;
import com.adaptcode.utp.dto.response.IntentoResponse;
import com.adaptcode.utp.model.Alternativa;
import com.adaptcode.utp.model.Bloque;
import com.adaptcode.utp.model.Curso;
import com.adaptcode.utp.model.MicroReto;
import com.adaptcode.utp.model.NivelMicroReto;
import com.adaptcode.utp.model.Retroalimentacion;
import com.adaptcode.utp.model.Tema;
import com.adaptcode.utp.model.TipoMicroReto;
import com.adaptcode.utp.repository.MicroRetoRepository;

class EvaluationServiceTest {

	private MicroRetoRepository microRetoRepository;
	private IntentoService intentoService;
	private EvaluationServiceImp evaluationService;

	@BeforeEach
	void configurar() {
		microRetoRepository = mock(MicroRetoRepository.class);
		intentoService = mock(IntentoService.class);
		evaluationService = new EvaluationServiceImp(
				microRetoRepository, intentoService);
	}

	@Test
	void evaluarRespuestaIncorrecta_devuelveFeedbackSinRevelarCorrecta() {
		MicroReto reto = retoSeleccionMultiple();
		when(microRetoRepository
				.findByIdAndActivoTrueAndTema_ActivoTrueAndTema_Curso_ActivoTrue(
						30L)).thenReturn(Optional.of(reto));
		when(intentoService.registrarEvaluacion(eq(9L), eq(30L), eq(false),
				eq(false), eq(1000L)))
				.thenReturn(new IntentoResponse(1L, 9L, 30L, 1, false, false,
						1000L));

		var response = evaluationService.evaluarIntento(9L,
				new EvaluarIntentoRequest(30L, 100L, null, false, 1000L));

		assertFalse(response.acierto());
		assertEquals("Confunde el operador.", response.retroalimentacion().causa());
		assertNull(response.solucion());
		assertFalse(response.verSolucionDisponible());
		verify(intentoService).registrarEvaluacion(eq(9L), eq(30L), eq(false),
				eq(false), eq(1000L));
	}

	@Test
	void ordenarBloques_soloAceptaElOrdenCorrectoCompleto() {
		MicroReto reto = retoOrdenarBloques();
		when(microRetoRepository
				.findByIdAndActivoTrueAndTema_ActivoTrueAndTema_Curso_ActivoTrue(
						30L)).thenReturn(Optional.of(reto));
		when(intentoService.registrarEvaluacion(eq(9L), eq(30L), eq(true),
				eq(false), eq(2000L)))
				.thenReturn(new IntentoResponse(1L, 9L, 30L, 1, true, false,
						2000L));
		when(intentoService.registrarEvaluacion(eq(9L), eq(30L), eq(false),
				eq(false), eq(2500L)))
				.thenReturn(new IntentoResponse(2L, 9L, 30L, 2, false, false,
						2500L));

		var correcta = evaluationService.evaluarIntento(9L,
				new EvaluarIntentoRequest(30L, null, List.of(300L, 301L, 302L),
						false, 2000L));
		var permutada = evaluationService.evaluarIntento(9L,
				new EvaluarIntentoRequest(30L, null, List.of(301L, 300L, 302L),
						false, 2500L));

		assertTrue(correcta.acierto());
		assertFalse(permutada.acierto());
		// La solución no se revela en el primer intento.
		assertNull(correcta.solucion());
	}

	@Test
	void ordenarBloques_noAceptaOrdenIncompletoNiRepetido() {
		MicroReto reto = retoOrdenarBloques();
		when(microRetoRepository
				.findByIdAndActivoTrueAndTema_ActivoTrueAndTema_Curso_ActivoTrue(
						30L)).thenReturn(Optional.of(reto));
		when(intentoService.registrarEvaluacion(eq(9L), eq(30L), eq(false),
				eq(false), any()))
				.thenReturn(new IntentoResponse(1L, 9L, 30L, 1, false, false,
						1000L));

		var incompleta = evaluationService.evaluarIntento(9L,
				new EvaluarIntentoRequest(30L, null, List.of(300L, 301L), false,
						1000L));
		var repetida = evaluationService.evaluarIntento(9L,
				new EvaluarIntentoRequest(30L, null,
						List.of(300L, 300L, 302L), false, 1000L));

		assertFalse(incompleta.acierto());
		assertFalse(repetida.acierto());
	}

	private MicroReto retoOrdenarBloques() {
		Curso curso = new Curso("Algoritmos");
		ReflectionTestUtils.setField(curso, "id", 1L);
		Tema tema = new Tema(curso, "Secuencias");
		ReflectionTestUtils.setField(tema, "id", 10L);
		MicroReto reto = new MicroReto("DEMO-ALG-02", tema,
				TipoMicroReto.ORDENAR_BLOQUES, NivelMicroReto.BASICO,
				"Ordena los pasos.", null, null);
		ReflectionTestUtils.setField(reto, "id", 30L);
		Bloque primero = new Bloque("Inicio", 1, 1);
		ReflectionTestUtils.setField(primero, "id", 300L);
		Bloque segundo = new Bloque("Proceso", 2, 2);
		ReflectionTestUtils.setField(segundo, "id", 301L);
		Bloque tercero = new Bloque("Fin", 3, 3);
		ReflectionTestUtils.setField(tercero, "id", 302L);
		reto.agregarBloque(primero);
		reto.agregarBloque(segundo);
		reto.agregarBloque(tercero);
		return reto;
	}

	private MicroReto retoSeleccionMultiple() {
		Curso curso = new Curso("Algoritmos");
		ReflectionTestUtils.setField(curso, "id", 1L);
		Tema tema = new Tema(curso, "Condicionales");
		ReflectionTestUtils.setField(tema, "id", 10L);
		MicroReto reto = new MicroReto("DEMO-ALG-01", tema,
				TipoMicroReto.SELECCION_MULTIPLE, NivelMicroReto.BASICO,
				"Evalúa una condición.", null, null);
		ReflectionTestUtils.setField(reto, "id", 30L);
		Alternativa incorrecta = new Alternativa("Falso", null, null, 1, false);
		ReflectionTestUtils.setField(incorrecta, "id", 100L);
		Alternativa correcta = new Alternativa("Verdadero", null, null, 2, true);
		ReflectionTestUtils.setField(correcta, "id", 101L);
		reto.agregarAlternativa(incorrecta);
		reto.agregarAlternativa(correcta);
		reto.agregarRetroalimentacion(new Retroalimentacion(incorrecta,
				"Confunde el operador.", "La condición usa >=.", "Paso 1.",
				"Reintenta."));
		return reto;
	}
}
