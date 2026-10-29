package com.adaptcode.utp.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.adaptcode.utp.model.RespuestaSala;

public interface RespuestaSalaRepository extends JpaRepository<RespuestaSala, Long> {

	Optional<RespuestaSala> findByParticipacion_IdAndPregunta_Id(
			Long participacionId, Long preguntaId);

	List<RespuestaSala> findAllByParticipacion_Id(Long participacionId);

	List<RespuestaSala> findAllByParticipacion_Estudiante_Id(Long estudianteId);

	boolean existsByParticipacion_IdAndPregunta_Id(Long participacionId, Long preguntaId);

	long countByParticipacion_Sala_IdAndPregunta_Id(Long salaId, Long preguntaId);

	List<RespuestaSala> findAllByParticipacion_Sala_Id(Long salaId);

	/**
	 * Ranking calculado en la base de datos: puntos = aciertos no omitidos,
	 * tiempo = suma del tiempo de esos aciertos y respondidas = envíos no
	 * omitidos. Excluye preguntas anuladas, participaciones expulsadas y, si se
	 * indica, la pregunta que sigue abierta: sus aciertos no deben filtrarse por
	 * puntos antes de que el docente cierre la ronda. Evita traer todas las
	 * respuestas y contarlas en memoria.
	 */
	@Query("""
			SELECT p.id AS participacionId,
			       COALESCE(SUM(CASE WHEN r.acierto = true AND r.omitida = false
			                         THEN 1 ELSE 0 END), 0) AS puntos,
			       COALESCE(SUM(CASE WHEN r.acierto = true AND r.omitida = false
			                         THEN COALESCE(r.tiempoMs, 0) ELSE 0 END), 0) AS tiempoMs,
			       COUNT(CASE WHEN r.omitida = false THEN 1 END) AS respondidas
			FROM ParticipacionSala p
			LEFT JOIN RespuestaSala r ON r.participacion = p
			  AND r.pregunta.id <> COALESCE(:excluirPreguntaId, -1)
			WHERE p.sala.id = :salaId
			  AND p.expulsado = false
			  AND (r IS NULL OR NOT EXISTS (
			       SELECT 1 FROM AnulacionPregunta a
			       WHERE a.sala.id = :salaId AND a.pregunta = r.pregunta))
			GROUP BY p.id
			""")
	List<PuntajeProyeccion> calcularPuntajes(@Param("salaId") Long salaId,
			@Param("excluirPreguntaId") Long excluirPreguntaId);

	interface PuntajeProyeccion {

		Long getParticipacionId();

		Long getPuntos();

		Long getTiempoMs();

		Long getRespondidas();
	}
}
