CREATE INDEX ix_ms_prd_producto_codigo ON ms_prd_producto (codigo);
CREATE INDEX ix_ms_prd_producto_nombre ON ms_prd_producto (nombre);
CREATE INDEX ix_ms_prd_producto_habilitado_stock ON ms_prd_producto (habilitado, STOCK_ACTUAL);
