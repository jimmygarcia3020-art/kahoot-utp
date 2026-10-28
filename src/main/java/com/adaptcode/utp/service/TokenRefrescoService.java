package com.adaptcode.utp.service;

import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.adaptcode.utp.repository.TokenRefrescoRepository;

import lombok.RequiredArgsConstructor;

/**
 * Revocaciones que deben persistirse aunque la operación que las origina
 * termine en error.
 *
 * <p>Cuando se detecta la reutilización de un refresh token se revoca toda la
 * cadena del usuario y después se lanza una excepción de autenticación. Si esa
 * revocación se hiciera dentro de la misma transacción, el rollback provocado
 * por la excepción la desharía y el atacante podría seguir usando los tokens.
 * Por eso se ejecuta en una transacción independiente ({@code REQUIRES_NEW}).</p>
 */
@Service
@RequiredArgsConstructor
public class TokenRefrescoService {

	private final TokenRefrescoRepository tokenRefrescoRepository;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void revocarTodos(Long usuarioId) {
		if (usuarioId == null) {
			return;
		}
		tokenRefrescoRepository.revocarTodos(usuarioId, Instant.now());
	}

	/**
	 * Limpieza diaria de tokens expirados. Sin esta tarea la tabla crecería con
	 * filas que ya no pueden renovar ninguna sesión.
	 */
	@Scheduled(cron = "${seguridad.refresh-limpieza-cron:0 0 3 * * *}")
	@Transactional
	public void limpiarExpirados() {
		tokenRefrescoRepository.eliminarExpirados(Instant.now());
	}
}
