package com.adaptcode.utp.dto.response;

import java.time.Instant;
import java.util.List;

import com.adaptcode.utp.model.EstadoSala;
import com.adaptcode.utp.model.NivelMicroReto;
import com.adaptcode.utp.model.TipoMicroReto;

/**
 * Pregunta habilitada para jugar una sala. Es la vista pública de
 * {@code PreguntaDocente}: incluye enunciado, alternativas y el estado del
 * cronómetro, pero <strong>nunca</strong> la alternativa correcta ni
 * retroalimentación.
 *
 * <p>Los campos {@code respondida}, {@code alternativaSeleccionadaId} y
 * {@code omitida} describen el envío del estudiante autenticado. Para el
 * docente propietario quedan en {@code false}, {@code null} y {@code false}
 * porque no tiene una participación propia.</p>
 */
public record PreguntaSalaResponse(
		Long salaId,
		EstadoSala estado,
		Long preguntaId,
		Integer numeroPregunta,
		Integer totalPreguntas,
		String enunciado,
		TipoMicroReto tipo,
		NivelMicroReto nivel,
		String tema,
		List<AlternativaSalaResponse> alternativas,
		Instant preguntaIniciadaEn,
		Instant pausadaEn,
		int segundosLimitePregunta,
		long milisegundosRestantes,
		boolean respondida,
		Long alternativaSeleccionadaId,
		boolean omitida) {
}
