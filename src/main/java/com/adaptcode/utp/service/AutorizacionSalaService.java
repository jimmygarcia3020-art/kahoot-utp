package com.adaptcode.utp.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.adaptcode.utp.exception.AccesoDenegadoException;
import com.adaptcode.utp.exception.ResourceNotFoundException;
import com.adaptcode.utp.model.RolUsuario;
import com.adaptcode.utp.model.Sala;
import com.adaptcode.utp.repository.ParticipacionSalaRepository;
import com.adaptcode.utp.repository.SalaRepository;
import com.adaptcode.utp.security.UsuarioPrincipal;

import lombok.RequiredArgsConstructor;

/**
 * Autorización horizontal de salas. Evita que un docente lea o modifique salas
 * de otro docente y que un estudiante consulte una sala en la que no participa.
 * Se apoya en el identificador del token, nunca en parámetros del cliente.
 */
@Service
@RequiredArgsConstructor
public class AutorizacionSalaService {

	private final SalaRepository salaRepository;
	private final ParticipacionSalaRepository participacionSalaRepository;

	/**
	 * Devuelve la sala solo si pertenece al docente indicado. Se responde 404
	 * (no 403) para no confirmar la existencia de salas ajenas.
	 */
	@Transactional(readOnly = true)
	public Sala validarDocentePropietario(Long salaId, Long docenteId) {
		if (salaId == null || salaId <= 0 || docenteId == null || docenteId <= 0) {
			throw new ResourceNotFoundException("La sala no existe.");
		}
		return salaRepository.findById(salaId)
				.filter(sala -> docenteId.equals(sala.getDocente().getId()))
				.orElseThrow(() -> new ResourceNotFoundException(
						"La sala no existe."));
	}

	/**
	 * Valida lectura de una sala: el docente propietario o un estudiante con
	 * participación registrada en ella. Cualquier otro caso es 403.
	 */
	@Transactional(readOnly = true)
	public Sala validarLectura(Long salaId, UsuarioPrincipal principal) {
		if (principal == null) {
			throw new AccesoDenegadoException();
		}
		if (principal.getRol() == RolUsuario.DOCENTE_UTP) {
			return validarDocentePropietario(salaId, principal.getId());
		}
		if (principal.getRol() == RolUsuario.ESTUDIANTE_UTP) {
			Sala sala = salaRepository.findById(salaId)
					.orElseThrow(() -> new ResourceNotFoundException(
							"La sala no existe."));
			boolean participa = participacionSalaRepository
					.existsBySala_IdAndEstudiante_Id(salaId, principal.getId());
			if (!participa) {
				throw new AccesoDenegadoException(
						"No tiene acceso a esta sala.");
			}
			return sala;
		}
		throw new AccesoDenegadoException();
	}
}
