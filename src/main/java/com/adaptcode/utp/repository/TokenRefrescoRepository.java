package com.adaptcode.utp.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.adaptcode.utp.model.TokenRefresco;

public interface TokenRefrescoRepository extends JpaRepository<TokenRefresco, Long> {

	Optional<TokenRefresco> findByTokenHash(String tokenHash);

	/**
	 * Sesiones activas de un usuario, de la más antigua a la más reciente. El
	 * id desempata cuando dos inicios de sesión comparten el mismo instante y
	 * se usa para revocar primero la sesión más vieja.
	 */
	List<TokenRefresco> findAllByUsuario_IdAndRevocadoFalseOrderByCreadoEnAscIdAsc(
			Long usuarioId);

	/**
	 * Revoca todos los tokens activos de un usuario. Se usa al cerrar todas las
	 * sesiones, al cambiar la contraseña y cuando se detecta reutilización de un
	 * token (posible robo).
	 */
	/**
	 * Revoca el token solo si sigue activo. Devuelve 0 cuando otro proceso ya
	 * lo rotó o revocó, lo que permite detectar carreras y reutilización sin
	 * emitir dos refresh tokens válidos a la vez.
	 */
	@Modifying
	@Transactional
	@Query("UPDATE TokenRefresco t SET t.revocado = true, t.revocadoEn = :ahora "
			+ "WHERE t.id = :id AND t.revocado = false")
	int revocarSiActivo(@Param("id") Long id, @Param("ahora") Instant ahora);

	@Modifying
	@Transactional
	@Query("UPDATE TokenRefresco t SET t.revocado = true, t.revocadoEn = :ahora "
			+ "WHERE t.usuario.id = :usuarioId AND t.revocado = false")
	int revocarTodos(@Param("usuarioId") Long usuarioId,
			@Param("ahora") Instant ahora);

	@Modifying
	@Transactional
	@Query("DELETE FROM TokenRefresco t WHERE t.expiraEn < :limite")
	int eliminarExpirados(@Param("limite") Instant limite);
}
