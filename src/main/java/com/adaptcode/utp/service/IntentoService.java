package com.adaptcode.utp.service;

import java.util.List;

import com.adaptcode.utp.dto.request.IntentoRequest;
import com.adaptcode.utp.dto.response.IntentoResponse;

public interface IntentoService {

	IntentoResponse registrarIntento(Long usuarioId, IntentoRequest request);

	/**
	 * Registra el resultado de una evaluación resuelta por el servidor. La ayuda
	 * se deriva del número de intento: desde el tercero la solución ya está
	 * disponible y el intento nunca entrega punto de dominio.
	 */
	IntentoResponse registrarEvaluacion(Long usuarioId, Long microRetoId,
			boolean acierto, boolean usoAyuda, Long tiempoMs);

	IntentoResponse mostrarPorId(Long usuarioId, Long id);

	List<IntentoResponse> listarPorUsuarioYReto(Long usuarioId, Long microRetoId);
}
