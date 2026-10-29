package com.adaptcode.utp.service;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.adaptcode.utp.dto.response.RankingSalaResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Caché de corta vida para el ranking de cada sala. El cálculo recorre todas
 * las participaciones y agrega en SQL, así que se evita repetirlo cuando varios
 * estudiantes consultan el ranking a la vez.
 *
 * <p>La entrada caduca a los 5 segundos (cota de obsolescencia) y además se
 * invalida explícitamente al registrar respuestas, expulsar/readmitir
 * participantes o anular preguntas.</p>
 */
@Component
public class RankingCache {

	private static final Duration VIGENCIA = Duration.ofSeconds(5);
	private static final long MAXIMO_SALAS = 500L;

	private final Cache<Long, List<RankingSalaResponse>> cache = Caffeine.newBuilder()
			.maximumSize(MAXIMO_SALAS)
			.expireAfterWrite(VIGENCIA)
			.build();

	public List<RankingSalaResponse> obtener(Long salaId,
			Supplier<List<RankingSalaResponse>> cargador) {
		return cache.get(salaId, id -> cargador.get());
	}

	public void invalidar(Long salaId) {
		if (salaId != null) {
			cache.invalidate(salaId);
		}
	}

	public void invalidarTodo() {
		cache.invalidateAll();
	}
}
