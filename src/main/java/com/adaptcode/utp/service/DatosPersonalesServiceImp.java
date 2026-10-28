package com.adaptcode.utp.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.adaptcode.utp.dto.request.EliminarCuentaRequest;
import com.adaptcode.utp.dto.response.DatosPersonalesResponse;
import com.adaptcode.utp.dto.response.MensajeResponse;
import com.adaptcode.utp.exception.AutenticacionException;
import com.adaptcode.utp.exception.ResourceNotFoundException;
import com.adaptcode.utp.model.InsigniaObtenida;
import com.adaptcode.utp.model.Intento;
import com.adaptcode.utp.model.ParticipacionSala;
import com.adaptcode.utp.model.PreferenciaAccesibilidad;
import com.adaptcode.utp.model.ProgresoMicroReto;
import com.adaptcode.utp.model.RespuestaSala;
import com.adaptcode.utp.model.Usuario;
import com.adaptcode.utp.repository.CodigoVerificacionRepository;
import com.adaptcode.utp.repository.InsigniaObtenidaRepository;
import com.adaptcode.utp.repository.IntentoRepository;
import com.adaptcode.utp.repository.ParticipacionSalaRepository;
import com.adaptcode.utp.repository.PreferenciaAccesibilidadRepository;
import com.adaptcode.utp.repository.ProgresoMicroRetoRepository;
import com.adaptcode.utp.repository.RespuestaSalaRepository;
import com.adaptcode.utp.repository.TokenRefrescoRepository;
import com.adaptcode.utp.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DatosPersonalesServiceImp implements DatosPersonalesService {

	private final UsuarioRepository usuarioRepository;
	private final ParticipacionSalaRepository participacionSalaRepository;
	private final RespuestaSalaRepository respuestaSalaRepository;
	private final IntentoRepository intentoRepository;
	private final InsigniaObtenidaRepository insigniaObtenidaRepository;
	private final ProgresoMicroRetoRepository progresoMicroRetoRepository;
	private final PreferenciaAccesibilidadRepository preferenciaAccesibilidadRepository;
	private final CodigoVerificacionRepository codigoVerificacionRepository;
	private final TokenRefrescoRepository tokenRefrescoRepository;
	private final PasswordEncoder passwordEncoder;

	@Override
	@Transactional(readOnly = true)
	public DatosPersonalesResponse exportar(Long usuarioId) {
		Usuario usuario = buscar(usuarioId);
		List<ParticipacionSala> participaciones = participacionSalaRepository
				.findAllByEstudiante_Id(usuarioId);
		List<RespuestaSala> respuestas = respuestaSalaRepository
				.findAllByParticipacion_Estudiante_Id(usuarioId);
		List<Intento> intentos = intentoRepository.findAllByUsuario_Id(usuarioId);
		List<InsigniaObtenida> insignias = insigniaObtenidaRepository
				.findAllByUsuario_Id(usuarioId);
		List<ProgresoMicroReto> progresos = progresoMicroRetoRepository
				.findAllByUsuario_Id(usuarioId);
		return new DatosPersonalesResponse(
				usuario.getId(), usuario.getCorreo(), usuario.getNombres(),
				usuario.getApellidos(), usuario.getRol(), usuario.getGenero(),
				usuario.getFechaNacimiento(), usuario.isActivo(),
				usuario.getCreadoEn(),
				participaciones.stream()
						.map(p -> new DatosPersonalesResponse.Participacion(
								p.getId(), p.getSala().getId(), p.isExpulsado(),
								p.getCreadoEn()))
						.toList(),
				respuestas.stream()
						.map(r -> new DatosPersonalesResponse.Respuesta(
								r.getId(), r.getParticipacion().getSala().getId(),
								r.getPregunta().getId(), r.isAcierto(),
								r.isOmitida(), r.getTiempoMs(), r.getCreadoEn()))
						.toList(),
				intentos.stream()
						.map(i -> new DatosPersonalesResponse.IntentoDatos(
								i.getId(), i.getMicroReto().getId(),
								i.getNroIntento(), i.isAcierto(), i.isUsoAyuda(),
								i.getTiempoMs(), i.getCreadoEn()))
						.toList(),
				insignias.stream()
						.map(i -> new DatosPersonalesResponse.Insignia(
								i.getId(), i.getTipo(), i.getObtenidoEn()))
						.toList(),
				progresos.stream()
						.map(p -> new DatosPersonalesResponse.Progreso(
								p.getId(), p.getMicroReto().getId(),
								p.isCompletado(), p.isResueltoConAyuda(),
								p.isPuntoDominio(), p.getActualizadoEn()))
						.toList(),
				preferenciaAccesibilidadRepository.findByUsuario_Id(usuarioId)
						.map(this::aPreferencia)
						.orElse(null));
	}

	@Override
	@Transactional
	public MensajeResponse eliminar(Long usuarioId, EliminarCuentaRequest request) {
		Usuario usuario = buscar(usuarioId);
		if (!usuario.isActivo()) {
			throw new AutenticacionException("La cuenta ya está desactivada.");
		}
		if (!passwordEncoder.matches(request.contrasena(),
				usuario.getContrasenaHash())) {
			throw new AutenticacionException("La contraseña no es correcta.");
		}
		// Se borran códigos pendientes y preferencias, y se anonimiza el perfil.
		// Las participaciones y respuestas quedan sin vínculo a una persona.
		codigoVerificacionRepository.deleteAllByCorreo(usuario.getCorreo());
		preferenciaAccesibilidadRepository.findByUsuario_Id(usuarioId)
				.ifPresent(preferenciaAccesibilidadRepository::delete);
		usuario.setCorreo("eliminado-" + usuarioId + "@anonimo.invalid");
		usuario.setNombres("Usuario");
		usuario.setApellidos("Eliminado");
		usuario.setContrasenaHash(passwordEncoder.encode(UUID.randomUUID().toString()));
		usuario.setGenero(null);
		usuario.setFechaNacimiento(null);
		usuario.setActivo(false);
		usuario.setCredencialCambiadaEn(Instant.now());
		usuarioRepository.save(usuario);
		tokenRefrescoRepository.revocarTodos(usuarioId, Instant.now());
		return new MensajeResponse(
				"Cuenta eliminada. Sus datos personales fueron anonimizados y "
						+ "todas las sesiones se cerraron.");
	}

	private Usuario buscar(Long usuarioId) {
		return usuarioRepository.findById(usuarioId)
				.orElseThrow(() -> new ResourceNotFoundException(
						"El usuario no existe."));
	}

	private DatosPersonalesResponse.Preferencia aPreferencia(
			PreferenciaAccesibilidad preferencia) {
		return new DatosPersonalesResponse.Preferencia(
				preferencia.getTema(), preferencia.getTamanoTexto(),
				preferencia.isReduccionMovimiento(),
				preferencia.isSonidoActivado(), preferencia.isModoDaltonismo());
	}
}
