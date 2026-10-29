package com.adaptcode.utp.service;

import java.util.List;

import com.adaptcode.utp.dto.request.SalaRequest;
import com.adaptcode.utp.dto.response.EstadoPreguntaResponse;
import com.adaptcode.utp.dto.response.PreguntaSalaResponse;
import com.adaptcode.utp.dto.response.SalaResponse;
import com.adaptcode.utp.model.EstadoSala;

public interface SalaService {

	SalaResponse crearSala(Long docenteId, SalaRequest request);

	SalaResponse mostrarPorId(Long docenteId, Long id);

	SalaResponse mostrarPorPin(String pin);

	List<SalaResponse> listarPorDocente(Long docenteId, EstadoSala estado);

	SalaResponse abrirEspera(Long docenteId, Long id);

	SalaResponse iniciarSala(Long docenteId, Long id);

	SalaResponse habilitarSiguientePregunta(Long docenteId, Long id);

	SalaResponse pausarSala(Long docenteId, Long id);

	SalaResponse reanudarSala(Long docenteId, Long id);

	SalaResponse finalizarSala(Long docenteId, Long id);

	EstadoPreguntaResponse estadoPregunta(Long salaId);

	/**
	 * Pregunta habilitada para jugar. {@code estudianteId} es opcional: si se
	 * indica, la respuesta incluye si ese estudiante ya envió su respuesta.
	 */
	PreguntaSalaResponse preguntaActual(Long salaId, Long estudianteId);
}
