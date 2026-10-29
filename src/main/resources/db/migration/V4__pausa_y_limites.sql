-- Iteracion 4: pausa de la sala y limites de consulta.
--
-- La pausa congela el cronometro de la pregunta. Al reanudar, el servicio
-- desplaza pregunta_iniciada_en por la duracion de la pausa, de modo que el
-- tiempo detenido no consume el plazo de respuesta de los estudiantes.

ALTER TABLE salas ADD COLUMN IF NOT EXISTS pausada_en TIMESTAMP WITH TIME ZONE;

-- Consultas del ranking y del flujo en vivo. Son idempotentes para que esta
-- migracion pueda aplicarse sobre una base ya creada por Hibernate.
CREATE INDEX IF NOT EXISTS idx_participaciones_sala_sala
	ON participaciones_sala (sala_id);
CREATE INDEX IF NOT EXISTS idx_respuestas_sala_pregunta
	ON respuestas_sala (pregunta_id);
CREATE INDEX IF NOT EXISTS idx_respuestas_sala_participacion
	ON respuestas_sala (participacion_id);
CREATE INDEX IF NOT EXISTS idx_codigos_correo_tipo_creado
	ON codigos_verificacion (correo, tipo, creado_en);
CREATE INDEX IF NOT EXISTS idx_tokens_refresco_usuario_revocado
	ON tokens_refresco (usuario_id, revocado);
