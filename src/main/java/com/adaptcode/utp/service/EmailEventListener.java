package com.adaptcode.utp.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Envía el código por correo después de que la transacción haya confirmado.
 * Si el envío falla, el código ya quedó persistido y el usuario puede pedir un
 * reenvío; nunca se pierde la transacción por un problema de SMTP.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailEventListener {

	private final EmailService emailService;

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT,
			fallbackExecution = true)
	public void alGenerarCodigo(CodigoEmailEvento evento) {
		try {
			emailService.enviarCodigo(evento.correo(), evento.codigo(),
					evento.tipo(), evento.expiraEn());
		} catch (RuntimeException ex) {
			// EmailServiceImp ya captura los errores de SMTP; esta red de
			// seguridad evita que una excepción del executor quede sin log.
			log.error("No se pudo procesar el envío del código {} a {}: {}",
					evento.tipo(), ofuscar(evento.correo()), ex.getMessage());
		}
	}

	private String ofuscar(String correo) {
		if (correo == null || correo.isBlank()) {
			return "(sin correo)";
		}
		int arroba = correo.indexOf('@');
		return arroba <= 1 ? "***"
				: correo.charAt(0) + "***" + correo.substring(arroba);
	}
}
