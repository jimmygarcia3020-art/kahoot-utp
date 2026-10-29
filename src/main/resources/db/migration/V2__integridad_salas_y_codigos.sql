-- Iteracion 1: integridad del puntaje de salas, bloqueo optimista y codigos
-- hasheados. Todas las sentencias son idempotentes para poder adoptar una base
-- que ya tenia el esquema creado por Hibernate antes de Flyway.

-- Pregunta habilitada por el docente y momento en que empezo el cronometro.
-- El servidor califica y mide el tiempo; el cliente deja de ser autoridad.
ALTER TABLE salas ADD COLUMN IF NOT EXISTS pregunta_actual_id BIGINT;
ALTER TABLE salas ADD COLUMN IF NOT EXISTS pregunta_iniciada_en TIMESTAMP WITH TIME ZONE;

-- Bloqueo optimista: evita que dos escrituras simultaneas se pisen.
ALTER TABLE salas ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE participaciones_sala ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE respuestas_sala ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Alternativa elegida: permite recalcular el puntaje y auditar la respuesta.
ALTER TABLE respuestas_sala ADD COLUMN IF NOT EXISTS alternativa_elegida_id BIGINT;

-- Los codigos ya no se guardan en claro: HMAC-SHA256 hexadecimal (64 chars).
ALTER TABLE codigos_verificacion ALTER COLUMN codigo TYPE VARCHAR(64);

DO $$
BEGIN
	IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_salas_pregunta_actual') THEN
		ALTER TABLE salas
			ADD CONSTRAINT fk_salas_pregunta_actual
			FOREIGN KEY (pregunta_actual_id) REFERENCES preguntas_docente (id)
			ON DELETE SET NULL;
	END IF;
	IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_respuestas_alternativa_elegida') THEN
		ALTER TABLE respuestas_sala
			ADD CONSTRAINT fk_respuestas_alternativa_elegida
			FOREIGN KEY (alternativa_elegida_id) REFERENCES alternativas_docente (id)
			ON DELETE SET NULL;
	END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_salas_pregunta_actual
	ON salas (pregunta_actual_id);
CREATE INDEX IF NOT EXISTS idx_respuestas_sala_participacion
	ON respuestas_sala (participacion_id);
CREATE INDEX IF NOT EXISTS idx_codigos_correo_tipo_usado
	ON codigos_verificacion (correo, tipo, usado);
