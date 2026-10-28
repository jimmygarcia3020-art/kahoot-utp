package com.adaptcode.utp.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Valida las migraciones Flyway contra PostgreSQL real (no H2). El contexto de
 * Spring arranca con {@code ddl-auto=validate}: si una entidad y el esquema
 * migrado no coinciden, la prueba falla. Sin Docker se omite y la suite sigue
 * siendo ejecutable en cualquier máquina.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
class PostgresMigracionesTest {

	@Container
	static final PostgreSQLContainer POSTGRES =
			new PostgreSQLContainer("postgres:16-alpine")
					.withDatabaseName("adaptcode")
					.withUsername("adaptcode")
					.withPassword("adaptcode");

	@DynamicPropertySource
	static void datasource(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.datasource.driver-class-name",
				() -> "org.postgresql.Driver");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
		registry.add("spring.flyway.enabled", () -> "true");
	}

	@Autowired
	private DataSource dataSource;

	@Test
	void flywayAplicaTodasLasMigracionesYElEsquemaValida() throws Exception {
		try (Connection conexion = dataSource.getConnection();
				Statement sentencia = conexion.createStatement()) {
			try (ResultSet historial = sentencia.executeQuery(
					"SELECT count(*) FROM flyway_schema_history WHERE success")) {
				assertTrue(historial.next());
				assertEquals(4, historial.getInt(1),
						"Deben aplicarse V1 a V4 sobre una base vacía.");
			}
			try (ResultSet pausa = sentencia.executeQuery(
					"SELECT count(*) FROM information_schema.columns "
							+ "WHERE table_name = 'salas' AND column_name = 'pausada_en'")) {
				assertTrue(pausa.next());
				assertEquals(1, pausa.getInt(1),
						"V4 debe crear salas.pausada_en.");
			}
			try (ResultSet indice = sentencia.executeQuery(
					"SELECT count(*) FROM pg_indexes "
							+ "WHERE indexname = 'idx_respuestas_sala_pregunta'")) {
				assertTrue(indice.next());
				assertEquals(1, indice.getInt(1),
						"V4 debe crear el índice de respuestas por pregunta.");
			}
		}
	}

	@Test
	void volverAAplicarFlywayNoEjecutaNada() {
		MigrateResult resultado = Flyway.configure()
				.dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
						POSTGRES.getPassword())
				.locations("classpath:db/migration")
				.baselineOnMigrate(true)
				.baselineVersion("1")
				.load()
				.migrate();

		assertEquals(0, resultado.migrationsExecuted,
				"Las migraciones ya aplicadas no deben repetirse.");
	}
}
