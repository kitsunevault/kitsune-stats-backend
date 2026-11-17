-- Las tiendas de cada jugador para Mis torneos: las que agrega él (aunque no estén en Kitsune Vaults) y el estado
-- de sus tiendas de Kitsune Vaults. Una tienda desactivada no sale para elegirla al agregar un torneo y, si es de
-- Kitsune Vaults, sus torneos ya no se agregan solos (los que ya tenía se quedan).
--   tienda_id NULL: la agregó él (nombre y ciudad suyos).
--   tienda_id: tienda de Kitsune Vaults (tenant); solo se guarda su fila cuando cambia su estado (sin fila = activa).
-- (Nombre después de 2026-11-17-mis-torneos.sql por orden de letras: no depende de esas tablas.)

CREATE TABLE tiendas_jugador (
    id         BIGSERIAL    PRIMARY KEY,
    usuario_id BIGINT       NOT NULL,
    nombre     VARCHAR(120) NOT NULL,
    ciudad     VARCHAR(80),
    tienda_id  VARCHAR(60),
    activa     BOOLEAN      NOT NULL DEFAULT true,
    creada_en  TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX tiendas_jugador_usuario_idx ON tiendas_jugador (usuario_id);
CREATE UNIQUE INDEX tiendas_jugador_kitsune_idx ON tiendas_jugador (usuario_id, tienda_id) WHERE tienda_id IS NOT NULL;
CREATE UNIQUE INDEX tiendas_jugador_nombre_idx ON tiendas_jugador (usuario_id, lower(nombre)) WHERE tienda_id IS NULL;
