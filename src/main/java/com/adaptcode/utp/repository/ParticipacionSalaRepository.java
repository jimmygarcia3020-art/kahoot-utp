package com.adaptcode.utp.repository;

import java.util.List;


import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.adaptcode.utp.model.ParticipacionSala;

public interface ParticipacionSalaRepository extends JpaRepository<ParticipacionSala, Long> {

	@EntityGraph(attributePaths = { "estudiante", "sala" })
	List<ParticipacionSala> findAllBySala_Id(Long salaId);

	List<ParticipacionSala> findAllByEstudiante_Id(Long estudianteId);

	List<ParticipacionSala> findAllBySala_IdAndEstudiante_Id(Long salaId, Long estudianteId);

	boolean existsBySala_IdAndEstudiante_Id(Long salaId, Long estudianteId);

	long countBySala_IdAndExpulsadoFalse(Long salaId);
}
