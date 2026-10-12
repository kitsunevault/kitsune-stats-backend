-- Metas privados: un grupo con sus propias estadísticas que solo ven sus miembros. Se entra con
-- el link de invitación del dueño o aceptando una invitación por usuario. Sus partidas no cuentan
-- en el meta global (sí en las stats personales de cada jugador).
CREATE TABLE metas (
    id                 BIGSERIAL     PRIMARY KEY,
    nombre             VARCHAR(60)   NOT NULL,
    descripcion        VARCHAR(300),
    dueno_id           BIGINT        NOT NULL,
    dueno_username     VARCHAR(60)   NOT NULL,
    -- Link de invitación (/unirse/<codigo>); el dueño lo cambia si se filtra.
    codigo_invitacion  VARCHAR(20)   NOT NULL UNIQUE,
    -- Reglas opcionales: temporada (fechas de las partidas) y líderes permitidos (vacío = todos).
    inicia             DATE,
    termina            DATE,
    lideres            VARCHAR(20)[] NOT NULL DEFAULT '{}',
    -- Archivado: se sigue viendo pero ya no se registran partidas ni entra nadie.
    archivado          BOOLEAN       NOT NULL DEFAULT false,
    creado_en          TIMESTAMP     NOT NULL DEFAULT now(),
    CHECK (inicia IS NULL OR termina IS NULL OR termina >= inicia)
);
CREATE INDEX metas_dueno_idx ON metas (dueno_id);

CREATE TABLE meta_miembros (
    meta_id    BIGINT       NOT NULL REFERENCES metas (id) ON DELETE CASCADE,
    usuario_id BIGINT       NOT NULL,
    username   VARCHAR(60)  NOT NULL,
    unido_en   TIMESTAMP    NOT NULL DEFAULT now(),
    PRIMARY KEY (meta_id, usuario_id)
);
CREATE INDEX meta_miembros_usuario_idx ON meta_miembros (usuario_id);

-- Invitaciones por usuario pendientes de aceptar.
CREATE TABLE meta_invitaciones (
    meta_id     BIGINT       NOT NULL REFERENCES metas (id) ON DELETE CASCADE,
    usuario_id  BIGINT       NOT NULL,
    username    VARCHAR(60)  NOT NULL,
    invitado_en TIMESTAMP    NOT NULL DEFAULT now(),
    PRIMARY KEY (meta_id, usuario_id)
);
CREATE INDEX meta_invitaciones_usuario_idx ON meta_invitaciones (usuario_id);

ALTER TABLE partidas ADD COLUMN meta_id BIGINT REFERENCES metas (id);
-- Las de torneos de tienda siempre van al meta global.
ALTER TABLE partidas ADD CONSTRAINT partidas_meta_casual CHECK (meta_id IS NULL OR tipo = 'CASUAL');
CREATE INDEX partidas_meta_idx ON partidas (meta_id, jugada_en) WHERE meta_id IS NOT NULL;
