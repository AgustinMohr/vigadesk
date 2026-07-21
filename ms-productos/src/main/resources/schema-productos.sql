DROP TABLE IF EXISTS ms_prd_producto CASCADE;

CREATE TABLE ms_prd_producto (
    id                    BIGSERIAL PRIMARY KEY,
    nombre                VARCHAR(255) NOT NULL,
    descripcion           VARCHAR(500),
    stock_actual          INTEGER NOT NULL DEFAULT 0,
    stock_minimo          INTEGER NOT NULL DEFAULT 0,
    precio                NUMERIC(19,2) NOT NULL,
    descuento_promocional NUMERIC(19,2) DEFAULT 0,
    categoria             VARCHAR(50)
);