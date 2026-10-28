package com.adaptcode.utp.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.adaptcode.utp.dto.request.IntentoRequest;
import com.adaptcode.utp.dto.response.IntentoResponse;
import com.adaptcode.utp.exception.ResourceNotFoundException;
import com.adaptcode.utp.exception.SolicitudInvalidaException;
import com.adaptcode.utp.model.InsigniaObtenida;
import com.adaptcode.utp.model.Intento;
import com.adaptcode.utp.model.MicroReto;
import com.adaptcode.utp.model.ProgresoMicroReto;
import com.adaptcode.utp.model.TipoInsignia;
import com.adaptcode.utp.model.Usuario;
import com.adaptcode.utp.repository.InsigniaObtenidaRepository;
import com.adaptcode.utp.repository.IntentoRepository;
import com.adaptcode.utp.repository.MicroRetoRepository;
import com.adaptcode.utp.repository.ProgresoMicroRetoRepository;
import com.adaptcode.utp.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class IntentoServiceImp implements IntentoService {

	/**
	 * A partir de este intento la solución está disponible. Los intentos
	 * asistidos nunca entregan punto de dominio: el dominio exige resolver el
	 * reto sin ayuda antes de agotar las tres oportunidades.
	 */
	private static final int INTENTOS_PARA_SOLUCION = 3;

	private final IntentoRepository intentoRepository;
	private final UsuarioRepository usuarioRepository;
	private final MicroRetoRepository microRetoRepository;
	private final ProgresoMicroRetoRepository progresoMicroRetoRepository;
	private final InsigniaObtenidaRepository insigniaObtenidaRepository;

	/**
	 * Completa el reto con la solución explicada. El cliente no decide el
	 * acierto ni la ayuda: este endpoint solo se habilita después de tres
	 * intentos y siempre registra un intento asistido sin punto de dominio.
	 */
	@Override
	@Transactional
	public IntentoResponse registrarIntento(Long usuarioId, IntentoRequest request) {
		Usuario usuario = validarUsuario(usuarioId);
		MicroReto microReto = buscarReto(request.microRetoId());
		int previos = (int) intentoRepository.countByUsuario_IdAndMicroReto_Id(
				usuarioId, microReto.getId());
		if (previos < INTENTOS_PARA_SOLUCION) {
			throw new SolicitudInvalidaException(
					"La solución se habilita después de tres intentos. "
							+ "Sigue practicando y vuelve a intentarlo.");
		}
		return guardar(usuario, microReto, previos + 1, true, true,
				request.tiempoMs());
	}

	/**
	 * Registra una evaluación calificada por el servidor. La ayuda se deriva
	 * del número de intento: desde el tercero la solución ya está disponible y
	 * el intento nunca entrega punto de dominio.
	 */
	@Override
	@Transactional
	public IntentoResponse registrarEvaluacion(Long usuarioId, Long microRetoId,
			boolean acierto, boolean usoAyuda, Long tiempoMs) {
		validarIdentificador(microRetoId);
		Usuario usuario = validarUsuario(usuarioId);
		MicroReto microReto = buscarReto(microRetoId);
		int nroIntento = (int) intentoRepository
				.countByUsuario_IdAndMicroReto_Id(usuarioId, microRetoId) + 1;
		boolean ayudaEfectiva = usoAyuda || nroIntento >= INTENTOS_PARA_SOLUCION;
		return guardar(usuario, microReto, nroIntento, acierto, ayudaEfectiva,
				tiempoMs);
	}

	@Override
	@Transactional(readOnly = true)
	public IntentoResponse mostrarPorId(Long usuarioId, Long id) {
		validarIdentificador(id);
		validarUsuario(usuarioId);
		Intento intento = intentoRepository.findById(id)
				.filter(i -> usuarioId.equals(i.getUsuario().getId()))
				.orElseThrow(() -> new ResourceNotFoundException("El intento no existe."));
		return aResponse(intento);
	}

	@Override
	@Transactional(readOnly = true)
	public List<IntentoResponse> listarPorUsuarioYReto(Long usuarioId, Long microRetoId) {
		validarUsuario(usuarioId);
		validarIdentificador(microRetoId);
		return intentoRepository.listarPorUsuarioYReto(usuarioId, microRetoId).stream()
				.map(this::aResponse)
				.toList();
	}

	private IntentoResponse guardar(Usuario usuario, MicroReto microReto,
			int nroIntento, boolean acierto, boolean usoAyuda, Long tiempoMs) {
		Intento intento = new Intento(usuario, microReto, nroIntento, acierto,
				usoAyuda);
		intento.setTiempoMs(tiempoMs);
		Intento guardado = intentoRepository.save(intento);
		actualizarProgreso(usuario, microReto, acierto, usoAyuda);
		otorgarInsigniasAutomaticas(usuario, guardado);
		return aResponse(guardado);
	}

	private MicroReto buscarReto(Long microRetoId) {
		validarIdentificador(microRetoId);
		return microRetoRepository
				.findByIdAndActivoTrueAndTema_ActivoTrueAndTema_Curso_ActivoTrue(
						microRetoId)
				.orElseThrow(() -> new ResourceNotFoundException(
						"El micro-reto no existe."));
	}

	private Usuario validarUsuario(Long usuarioId) {
		validarIdentificador(usuarioId);
		Usuario usuario = usuarioRepository.findById(usuarioId)
				.orElseThrow(() -> new ResourceNotFoundException("El usuario no existe."));
		if (!usuario.isActivo()) {
			throw new SolicitudInvalidaException("El usuario no está activo.");
		}
		return usuario;
	}

	/**
	 * El primer acierto define el progreso. Si el reto ya estaba completado, no
	 * se degrada ni se vuelve a otorgar: el punto de dominio es permanente y
	 * solo lo obtiene quien resuelve sin ayuda.
	 */
	private void actualizarProgreso(
			Usuario usuario, MicroReto microReto, boolean acierto, boolean usoAyuda) {
		if (!acierto) {
			return;
		}
		ProgresoMicroReto progreso = progresoMicroRetoRepository
				.findByUsuario_IdAndMicroReto_Id(usuario.getId(), microReto.getId())
				.orElseGet(() -> new ProgresoMicroReto(usuario, microReto));
		if (!progreso.isCompletado()) {
			progreso.setCompletado(true);
			progreso.setResueltoConAyuda(usoAyuda);
			progreso.setPuntoDominio(!usoAyuda);
			progresoMicroRetoRepository.save(progreso);
		}
	}

	private void otorgarInsigniasAutomaticas(Usuario usuario, Intento intento) {
		if (intento.isAcierto()
				&& progresoMicroRetoRepository.countByUsuario_IdAndCompletadoTrue(
						usuario.getId()) == 1) {
			otorgarSiNoExiste(usuario, TipoInsignia.PRIMER_PASO);
		}
		if (intento.isAcierto() && intento.getNroIntento() >= INTENTOS_PARA_SOLUCION) {
			otorgarSiNoExiste(usuario, TipoInsignia.PERSEVERANTE);
		}
		// TODO(fase-gamificacion): EXPLORADOR, DOMINIO_BASICO y CURSO_COMPLETADO
		// requieren análisis transversal de cursos/temas y se otorgan en
		// ProgressService cuando exista el cálculo de completitud por curso.
	}

	private void otorgarSiNoExiste(Usuario usuario, TipoInsignia tipo) {
		if (!insigniaObtenidaRepository.existsByUsuario_IdAndTipo(
				usuario.getId(), tipo)) {
			insigniaObtenidaRepository.save(new InsigniaObtenida(usuario, tipo));
		}
	}

	private void validarIdentificador(Long id) {
		if (id == null || id <= 0) {
			throw new SolicitudInvalidaException("El identificador debe ser positivo.");
		}
	}

	private IntentoResponse aResponse(Intento intento) {
		return new IntentoResponse(
				intento.getId(),
				intento.getUsuario().getId(),
				intento.getMicroReto().getId(),
				intento.getNroIntento(),
				intento.isAcierto(),
				intento.isUsoAyuda(),
				intento.getTiempoMs());
	}
}
