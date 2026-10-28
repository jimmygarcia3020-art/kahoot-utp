package com.adaptcode.utp.service;

import java.time.Instant;

import com.adaptcode.utp.model.TipoCodigoVerificacion;

/**
 * Envío de códigos de verificación y recuperación. La implementación usa SMTP
 * cuando está configurado; sin SMTP (demo local) registra el aviso y deja que
 * el perfil demo exponga el código en la respuesta.
 */
public interface EmailService {

	void enviarCodigo(String correo, String codigo, TipoCodigoVerificacion tipo,
			Instant expiraEn);
}
