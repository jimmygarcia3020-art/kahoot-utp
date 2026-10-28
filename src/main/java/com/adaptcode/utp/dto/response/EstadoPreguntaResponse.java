package com.adaptcode.utp.dto.response;

import java.time.Instant;

import com.adaptcode.utp.model.EstadoSala;

/**
 * Estado del cronómetro de la pregunta habilitada. Nunca incluye el enunciado,
 * las alternativas ni la respuesta correcta.
 */
public record EstadoPreguntaResponse(
		Long salaId,
		EstadoSala estado,
		Long preguntaId,
		Instant preguntaIniciadaEn,
		Instant pausadaEn,
		int segundosLimitePregunta,
		long milisegundosRestantes,
		Long version) {
}
