package com.adaptcode.utp.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.adaptcode.utp.model.GeneroUsuario;
import com.adaptcode.utp.model.RolUsuario;
import com.adaptcode.utp.model.TipoInsignia;

/**
 * Copia completa de los datos personales del usuario (portabilidad). Incluye
 * perfil y actividad académica. No incluye hashes de contraseña ni tokens.
 */
public record DatosPersonalesResponse(
		Long id,
		String correo,
		String nombres,
		String apellidos,
		RolUsuario rol,
		GeneroUsuario genero,
		LocalDate fechaNacimiento,
		boolean activo,
		Instant creadoEn,
		List<Participacion> participaciones,
		List<Respuesta> respuestas,
		List<IntentoDatos> intentos,
		List<Insignia> insignias,
		List<Progreso> progresos,
		Preferencia preferencia) {

	public record Participacion(
			Long id,
			Long salaId,
			boolean expulsado,
			Instant creadoEn) {
	}

	public record Respuesta(
			Long id,
			Long salaId,
			Long preguntaId,
			boolean acierto,
			boolean omitida,
			Long tiempoMs,
			Instant creadoEn) {
	}

	public record IntentoDatos(
			Long id,
			Long microRetoId,
			Integer nroIntento,
			boolean acierto,
			boolean usoAyuda,
			Long tiempoMs,
			Instant creadoEn) {
	}

	public record Insignia(
			Long id,
			TipoInsignia tipo,
			Instant obtenidoEn) {
	}

	public record Progreso(
			Long id,
			Long microRetoId,
			boolean completado,
			boolean resueltoConAyuda,
			boolean puntoDominio,
			Instant actualizadoEn) {
	}

	public record Preferencia(
			String tema,
			String tamanoTexto,
			boolean reduccionMovimiento,
			boolean sonidoActivado,
			boolean modoDaltonismo) {
	}
}
