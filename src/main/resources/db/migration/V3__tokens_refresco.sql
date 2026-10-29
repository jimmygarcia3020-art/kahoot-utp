-- Tokens de refresco: se guarda solo el hash SHA-256 del token. Rotación de un
-- solo uso y revocación para cerrar sesión o ante reutilización (posible robo).
CREATE TABLE IF NOT EXISTS tokens_refresco (
	id BIGSERIAL PRIMARY KEY,
	usuario_id BIGINT NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
	token_hash VARCHAR(64) NOT NULL UNIQUE,
	expira_en TIMESTAMP WITH TIME ZONE NOT NULL,
	revocado BOOLEAN NOT NULL DEFAULT FALSE,
	revocado_en TIMESTAMP WITH TIME ZONE,
	creado_en TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tokens_refresco_usuario
	ON tokens_refresco (usuario_id);
CREATE INDEX IF NOT EXISTS idx_tokens_refresco_expira
	ON tokens_refresco (expira_en);
