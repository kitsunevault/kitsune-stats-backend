-- Mis decks y las rondas de cada torneo de Mis torneos.
--
-- Mis decks: los decks que juega cada jugador (nombre, juego, líder si es One Piece, lista y si ya lo retiró).
-- Un torneo se juega con uno de sus decks (deck_id; "mazo" se queda para los de antes).
--
-- Rondas: cómo le fue en cada ronda (rival, ganó/perdió/empate, si empezó). Las de One Piece con líderes y
-- ganada o perdida también son una partida de torneo (partida_id): cuentan en Mis stats y en el meta. Por eso una
-- partida de torneo ya no tiene que ser de una tienda de Kitsune Vaults (tienda_id y torneo_id juntos o ninguno).

CREATE TABLE mis_decks (
    id         BIGSERIAL    PRIMARY KEY,
    usuario_id BIGINT       NOT NULL,
    nombre     VARCHAR(80)  NOT NULL,
    juego      VARCHAR(60),
    lider      VARCHAR(20)  REFERENCES lideres (codigo),
    lista      VARCHAR(4000),
    retirado   BOOLEAN      NOT NULL DEFAULT false,
    creado_en  TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX mis_decks_usuario_idx ON mis_decks (usuario_id);
CREATE UNIQUE INDEX mis_decks_nombre_idx ON mis_decks (usuario_id, lower(nombre));

ALTER TABLE mis_torneos ADD COLUMN deck_id BIGINT REFERENCES mis_decks (id) ON DELETE SET NULL;

CREATE TABLE mis_torneos_rondas (
    id          BIGSERIAL   PRIMARY KEY,
    torneo_id   BIGINT      NOT NULL REFERENCES mis_torneos (id) ON DELETE CASCADE,
    ronda       INT         NOT NULL CHECK (ronda BETWEEN 1 AND 20),
    -- El deck del rival: su líder (One Piece) y/o un nombre libre (otros juegos).
    rival_lider VARCHAR(20) REFERENCES lideres (codigo),
    rival       VARCHAR(80),
    resultado   CHAR(1)     NOT NULL CHECK (resultado IN ('G', 'P', 'E')),
    empece      BOOLEAN,
    partida_id  BIGINT      REFERENCES partidas (id) ON DELETE SET NULL,
    UNIQUE (torneo_id, ronda)
);
CREATE INDEX mis_torneos_rondas_partida_idx ON mis_torneos_rondas (partida_id) WHERE partida_id IS NOT NULL;

-- Partidas de torneo: de una tienda de Kitsune Vaults (tienda_id + torneo_id) o de Mis torneos (sin tienda Kitsune).
DO $$
DECLARE
    nombre text;
BEGIN
    SELECT conname INTO nombre FROM pg_constraint
    WHERE conrelid = 'partidas'::regclass AND contype = 'c'
      AND pg_get_constraintdef(oid) LIKE '%tienda_id IS NOT NULL%torneo_id IS NOT NULL%ronda IS NOT NULL%';
    IF nombre IS NOT NULL THEN
        EXECUTE format('ALTER TABLE partidas DROP CONSTRAINT %I', nombre);
    END IF;
END $$;
ALTER TABLE partidas ADD CONSTRAINT partidas_torneo_check
    CHECK (tipo = 'CASUAL' OR (ronda IS NOT NULL AND (tienda_id IS NULL) = (torneo_id IS NULL)));
