-- Mis torneos: lo que cada jugador gasta y gana en torneos, en tiendas de Kitsune Vaults o no.
-- Los de tiendas Kitsune se agregan solos (tienda_id + torneo_tienda_id); los demás los captura él.
-- Es privado: solo lo ve su dueño.

CREATE TABLE mis_torneos (
    id               BIGSERIAL      PRIMARY KEY,
    usuario_id       BIGINT         NOT NULL,
    fecha            DATE           NOT NULL,
    nombre           VARCHAR(150),
    tienda_nombre    VARCHAR(120)   NOT NULL,
    -- Tienda de Kitsune Vaults (tenant) y el torneo allá: así no se vuelve a importar.
    tienda_id        VARCHAR(60),
    torneo_tienda_id BIGINT,
    juego            VARCHAR(60),
    mazo             VARCHAR(80),
    lugar            INT            CHECK (lugar > 0),
    jugadores        INT            CHECK (jugadores > 0),
    victorias        SMALLINT       CHECK (victorias >= 0),
    derrotas         SMALLINT       CHECK (derrotas >= 0),
    empates          SMALLINT       CHECK (empates >= 0),
    entrada          NUMERIC(10, 2) NOT NULL DEFAULT 0 CHECK (entrada >= 0),
    moneda           VARCHAR(3)     NOT NULL DEFAULT 'MXN',
    notas            VARCHAR(500),
    -- Uno importado que el jugador quitó: se queda oculto para no volver a importarlo.
    oculto           BOOLEAN        NOT NULL DEFAULT false,
    creado_en        TIMESTAMP      NOT NULL DEFAULT now()
);
CREATE INDEX mis_torneos_usuario_idx ON mis_torneos (usuario_id, fecha DESC);
CREATE UNIQUE INDEX mis_torneos_importado_idx ON mis_torneos (usuario_id, tienda_id, torneo_tienda_id)
    WHERE torneo_tienda_id IS NOT NULL;

-- Otros gastos del torneo: transporte, comida, hotel…
CREATE TABLE mis_torneos_gastos (
    id        BIGSERIAL      PRIMARY KEY,
    torneo_id BIGINT         NOT NULL REFERENCES mis_torneos (id) ON DELETE CASCADE,
    concepto  VARCHAR(60)    NOT NULL,
    monto     NUMERIC(10, 2) NOT NULL CHECK (monto >= 0),
    orden     INT            NOT NULL DEFAULT 0
);
CREATE INDEX mis_torneos_gastos_torneo_idx ON mis_torneos_gastos (torneo_id);

-- Premios: cartas, sobres, producto, crédito/puntos de tienda o efectivo.
--   valor: lo que vale (aprox.) lo que conserva o cambió; en EFECTIVO y CREDITO, el monto.
--   estado: CONSERVO / VENDIDA (precio_venta, vendida_en) / CAMBIADA.
CREATE TABLE mis_torneos_premios (
    id           BIGSERIAL      PRIMARY KEY,
    torneo_id    BIGINT         NOT NULL REFERENCES mis_torneos (id) ON DELETE CASCADE,
    tipo         VARCHAR(10)    NOT NULL CHECK (tipo IN ('CARTA', 'SOBRES', 'PRODUCTO', 'CREDITO', 'EFECTIVO')),
    descripcion  VARCHAR(150)   NOT NULL,
    cantidad     INT            NOT NULL DEFAULT 1 CHECK (cantidad > 0),
    valor        NUMERIC(10, 2) CHECK (valor >= 0),
    estado       VARCHAR(10)    NOT NULL DEFAULT 'CONSERVO' CHECK (estado IN ('CONSERVO', 'VENDIDA', 'CAMBIADA')),
    precio_venta NUMERIC(10, 2) CHECK (precio_venta >= 0),
    vendida_en   DATE,
    -- TIENDA: vino del torneo de una tienda Kitsune (sobres o puntos); MANUAL: lo anotó el jugador.
    origen       VARCHAR(10)    NOT NULL DEFAULT 'MANUAL' CHECK (origen IN ('TIENDA', 'MANUAL')),
    orden        INT            NOT NULL DEFAULT 0
);
CREATE INDEX mis_torneos_premios_torneo_idx ON mis_torneos_premios (torneo_id);
