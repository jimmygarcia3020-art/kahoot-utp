package com.adaptcode.utp.integration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.adaptcode.utp.model.EstadoSala;
import com.adaptcode.utp.model.GeneroUsuario;
import com.adaptcode.utp.model.Quiz;
import com.adaptcode.utp.model.RolUsuario;
import com.adaptcode.utp.model.Sala;
import com.adaptcode.utp.model.Usuario;
import com.adaptcode.utp.repository.QuizRepository;
import com.adaptcode.utp.repository.SalaRepository;
import com.adaptcode.utp.repository.UsuarioRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.RollbackException;

/**
 * Verifica que el bloqueo optimista de {@link Sala} impide que dos escrituras
 * concurrentes se pisen. Se usan dos {@link EntityManager} independientes
 * (dos sesiones reales): dos {@code find} en la misma sesión devolverían la
 * misma instancia administrada y no habría conflicto.
 *
 * <p>No lleva {@code @Transactional}: la prueba necesita confirmar la primera
 * escritura para que la segunda, con la versión vieja, falle al hacer commit.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class ConcurrenciaSalaIntegrationTest {

	@Autowired
	private SalaRepository salaRepository;

	@Autowired
	private QuizRepository quizRepository;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	private Usuario docente;
	private Quiz quiz;
	private Sala sala;

	@BeforeEach
	void preparar() {
		docente = usuarioRepository.save(new Usuario("c2026999@utp.edu.pe",
				"Docente", "Concurrencia", "hash-no-usado", RolUsuario.DOCENTE_UTP,
				true, GeneroUsuario.MASCULINO, LocalDate.of(1985, 3, 20)));
		quiz = quizRepository.save(new Quiz(docente, "Quiz de concurrencia"));
		sala = salaRepository.saveAndFlush(
				new Sala(docente, quiz, "998877", 30));
	}

	@AfterEach
	void limpiar() {
		salaRepository.deleteAll();
		quizRepository.deleteAll();
		usuarioRepository.deleteAll();
	}

	@Test
	void laSegundaEscrituraConVersionViejaFallaPorBloqueoOptimista() {
		EntityManager primeraSesion = entityManagerFactory.createEntityManager();
		EntityManager segundaSesion = entityManagerFactory.createEntityManager();
		List<EntityManager> sesiones = new ArrayList<>(List.of(primeraSesion,
				segundaSesion));
		try {
			primeraSesion.getTransaction().begin();
			segundaSesion.getTransaction().begin();

			Sala primeraCopia = primeraSesion.find(Sala.class, sala.getId());
			Sala segundaCopia = segundaSesion.find(Sala.class, sala.getId());
			assertNotNull(primeraCopia);
			assertNotNull(segundaCopia);

			primeraCopia.setEstado(EstadoSala.EN_CURSO);
			primeraSesion.getTransaction().commit();

			segundaCopia.setEstado(EstadoSala.FINALIZADA);
			RollbackException conflicto = assertThrows(RollbackException.class,
					() -> segundaSesion.getTransaction().commit());
			assertTrue(tieneConflictoDeVersion(conflicto),
					"Se esperaba una causa de versión obsoleta: " + cadena(conflicto));
		} finally {
			sesiones.forEach(sesion -> {
				if (sesion.getTransaction().isActive()) {
					sesion.getTransaction().rollback();
				}
				sesion.close();
			});
		}
	}

	private boolean tieneConflictoDeVersion(Throwable error) {
		Throwable actual = error;
		while (actual != null) {
			if (actual instanceof OptimisticLockException
					|| actual instanceof org.hibernate.StaleStateException) {
				return true;
			}
			actual = actual.getCause();
		}
		return false;
	}

	private String cadena(Throwable error) {
		StringBuilder texto = new StringBuilder();
		Throwable actual = error;
		while (actual != null) {
			texto.append(actual.getClass().getSimpleName()).append(": ")
					.append(actual.getMessage()).append(" <- ");
			actual = actual.getCause();
		}
		return texto.toString();
	}
}
