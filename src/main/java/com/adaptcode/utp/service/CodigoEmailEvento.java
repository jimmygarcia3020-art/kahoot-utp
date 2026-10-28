package com.adaptcode.utp.service;

import java.time.Instant;

import com.adaptcode.utp.model.TipoCodigoVerificacion;

/**
 * Evento de dominio que se publica al generar un código. Se entrega al
 * servicio de correo solo después del commit y de forma asíncrona.
 */
public record CodigoEmailEvento(
		String correo,
		String codigo,
		TipoCodigoVerificacion tipo,
		Instant expiraEn) {
}
