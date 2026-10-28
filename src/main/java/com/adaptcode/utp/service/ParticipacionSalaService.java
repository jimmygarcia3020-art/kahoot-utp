package com.adaptcode.utp.service;

import java.util.List;

import com.adaptcode.utp.dto.request.ParticipacionSalaRequest;
import com.adaptcode.utp.dto.response.ParticipacionSalaResponse;

public interface ParticipacionSalaService {

	ParticipacionSalaResponse unirse(Long salaId, ParticipacionSalaRequest request);

	ParticipacionSalaResponse mostrarPorId(Long salaId, Long id);

	List<ParticipacionSalaResponse> listarPorSala(Long salaId);

	List<ParticipacionSalaResponse> listarPorSala(Long salaId, Long estudianteId);

	/**
	 * Comprueba que la participación pertenezca al estudiante autenticado.
	 * Responde igual que si no existiera para no filtrar identificadores.
	 */
	void validarPropietario(Long salaId, Long participacionId, Long estudianteId);

	ParticipacionSalaResponse expulsar(Long docenteId, Long salaId, Long id);

	ParticipacionSalaResponse readmitir(Long docenteId, Long salaId, Long id);
}
