package com.adaptcode.utp.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.adaptcode.utp.dto.request.ParticipacionSalaRequest;
import com.adaptcode.utp.dto.response.ParticipacionSalaResponse;
import com.adaptcode.utp.exception.AccesoDenegadoException;
import com.adaptcode.utp.exception.ResourceNotFoundException;
import com.adaptcode.utp.exception.SolicitudInvalidaException;
import com.adaptcode.utp.model.EstadoSala;
import com.adaptcode.utp.model.ParticipacionSala;
import com.adaptcode.utp.model.RolUsuario;
import com.adaptcode.utp.model.Sala;
import com.adaptcode.utp.model.Usuario;
import com.adaptcode.utp.repository.ParticipacionSalaRepository;
import com.adaptcode.utp.repository.SalaRepository;
import com.adaptcode.utp.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ParticipacionSalaServiceImp implements ParticipacionSalaService {

	private final ParticipacionSalaRepository participacionSalaRepository;
	private final SalaRepository salaRepository;
	private final UsuarioRepository usuarioRepository;
	private final RankingCache rankingCache;
	private final SalaEventoService salaEventoService;

	@Override
	@Transactional
	public ParticipacionSalaResponse unirse(
			Long salaId, ParticipacionSalaRequest request) {
		Sala sala = buscarSala(salaId);
		if (!pinCoincide(request.pin(), sala.getPin())) {
			// 403 y comparación en tiempo constante: no se confirma si el PIN
			// existe en otra sala ni se filtra cuánto coincide.
			throw new AccesoDenegadoException(
					"El PIN no corresponde a esta sala.");
		}
		if (sala.getEstado() != EstadoSala.SALA_ESPERA) {
			throw new SolicitudInvalidaException(
					"Solo se puede ingresar durante la sala de espera.");
		}
		Usuario estudiante = validarEstudiante(request.estudianteId());
		if (participacionSalaRepository.existsBySala_IdAndEstudiante_Id(
				salaId, estudiante.getId())) {
			throw new SolicitudInvalidaException(
					"Cada cuenta ocupa un único lugar en la sala.");
		}
		ParticipacionSala participacion = new ParticipacionSala(sala, estudiante);
		return guardarYPublicar(salaId, participacion);
	}

	@Override
	@Transactional(readOnly = true)
	public ParticipacionSalaResponse mostrarPorId(Long salaId, Long id) {
		validarIdentificador(id);
		ParticipacionSala participacion = buscarEnSala(salaId, id);
		return aResponse(participacion);
	}

	@Override
	@Transactional(readOnly = true)
	public List<ParticipacionSalaResponse> listarPorSala(Long salaId) {
		buscarSala(salaId);
		return participacionSalaRepository.findAllBySala_Id(salaId).stream()
				.map(this::aResponse)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<ParticipacionSalaResponse> listarPorSala(Long salaId, Long estudianteId) {
		validarIdentificador(estudianteId);
		buscarSala(salaId);
		return participacionSalaRepository
				.findAllBySala_IdAndEstudiante_Id(salaId, estudianteId).stream()
				.map(this::aResponse)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public void validarPropietario(Long salaId, Long participacionId, Long estudianteId) {
		validarIdentificador(participacionId);
		validarIdentificador(estudianteId);
		boolean propia = participacionSalaRepository.findById(participacionId)
				.filter(p -> salaId != null && salaId.equals(p.getSala().getId()))
				.filter(p -> p.getEstudiante() != null
						&& estudianteId.equals(p.getEstudiante().getId()))
				.isPresent();
		if (!propia) {
			throw new ResourceNotFoundException("La participación no existe.");
		}
	}

	@Override
	@Transactional
	public ParticipacionSalaResponse expulsar(
			Long docenteId, Long salaId, Long id) {
		Sala sala = buscarSalaDeDocente(docenteId, salaId);
		if (sala.getEstado() != EstadoSala.BORRADOR
				&& sala.getEstado() != EstadoSala.SALA_ESPERA) {
			throw new SolicitudInvalidaException(
					"Solo se puede expulsar antes de iniciar la sala.");
		}
		ParticipacionSala participacion = buscarEnSala(salaId, id);
		participacion.setExpulsado(true);
		return guardarYPublicar(salaId, participacion);
	}

	@Override
	@Transactional
	public ParticipacionSalaResponse readmitir(
			Long docenteId, Long salaId, Long id) {
		Sala sala = buscarSalaDeDocente(docenteId, salaId);
		if (sala.getEstado() != EstadoSala.BORRADOR
				&& sala.getEstado() != EstadoSala.SALA_ESPERA) {
			throw new SolicitudInvalidaException(
					"Solo se puede readmitir antes de iniciar la sala.");
		}
		ParticipacionSala participacion = buscarEnSala(salaId, id);
		participacion.setExpulsado(false);
		return guardarYPublicar(salaId, participacion);
	}

	private ParticipacionSalaResponse guardarYPublicar(Long salaId,
			ParticipacionSala participacion) {
		ParticipacionSala guardada = participacionSalaRepository.save(participacion);
		rankingCache.invalidar(salaId);
		long participantes = participacionSalaRepository
				.countBySala_IdAndExpulsadoFalse(salaId);
		salaEventoService.publicarTrasCommit(salaId,
				SalaEventoService.EVENTO_PARTICIPANTES,
				Map.of("salaId", salaId, "participantes", participantes));
		return aResponse(guardada);
	}

	private ParticipacionSala buscarEnSala(Long salaId, Long id) {
		buscarSala(salaId);
		return participacionSalaRepository.findById(id)
				.filter(p -> salaId.equals(p.getSala().getId()))
				.orElseThrow(() -> new ResourceNotFoundException(
						"La participación no existe."));
	}

	private Sala buscarSala(Long salaId) {
		validarIdentificador(salaId);
		return salaRepository.findById(salaId)
				.orElseThrow(() -> new ResourceNotFoundException(
						"La sala no existe."));
	}

	private Sala buscarSalaDeDocente(Long docenteId, Long salaId) {
		validarIdentificador(docenteId);
		Sala sala = buscarSala(salaId);
		if (!docenteId.equals(sala.getDocente().getId())) {
			throw new ResourceNotFoundException("La sala no existe.");
		}
		return sala;
	}

	private Usuario validarEstudiante(Long estudianteId) {
		validarIdentificador(estudianteId);
		Usuario estudiante = usuarioRepository.findById(estudianteId)
				.orElseThrow(() -> new ResourceNotFoundException(
						"El estudiante no existe."));
		if (estudiante.getRol() != RolUsuario.ESTUDIANTE_UTP
				|| !estudiante.isActivo()) {
			throw new SolicitudInvalidaException(
					"Solo un estudiante UTP activo puede participar en salas.");
		}
		return estudiante;
	}

	private boolean pinCoincide(String enviado, String esperado) {
		if (enviado == null || esperado == null) {
			return false;
		}
		return MessageDigest.isEqual(
				enviado.getBytes(StandardCharsets.UTF_8),
				esperado.getBytes(StandardCharsets.UTF_8));
	}

	private void validarIdentificador(Long id) {
		if (id == null || id <= 0) {
			throw new SolicitudInvalidaException("El identificador debe ser positivo.");
		}
	}

	private ParticipacionSalaResponse aResponse(ParticipacionSala participacion) {
		return new ParticipacionSalaResponse(
				participacion.getId(),
				participacion.getSala().getId(),
				participacion.getEstudiante().getId(),
				participacion.isExpulsado());
	}
}
