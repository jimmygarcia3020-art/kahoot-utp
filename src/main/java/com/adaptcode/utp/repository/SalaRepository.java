package com.adaptcode.utp.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.adaptcode.utp.model.EstadoSala;
import com.adaptcode.utp.model.Sala;

public interface SalaRepository extends JpaRepository<Sala, Long> {

	/**
	 * Carga en una sola consulta las relaciones que usan el estado de la sala,
	 * el ranking y los mapeos a DTO. Evita el N+1 en cada lectura.
	 */
	@EntityGraph(attributePaths = { "docente", "quizOrigen", "preguntaActual" })
	Optional<Sala> findById(Long id);

	@EntityGraph(attributePaths = { "docente", "quizOrigen", "preguntaActual" })
	Optional<Sala> findByPin(String pin);

	List<Sala> findAllByDocente_IdOrderByIdDesc(Long docenteId);

	boolean existsByPin(String pin);

	// ! CONSULTA PARA OBTENER LAS SALAS DE UN DOCENTE POR ESTADO
	@Query("SELECT s FROM Sala s JOIN FETCH s.quizOrigen WHERE s.docente.id = :docenteId AND s.estado = :estado ORDER BY s.id DESC")
	List<Sala> buscarPorDocenteYEstado(
			@Param("docenteId") Long docenteId, @Param("estado") EstadoSala estado);
}
