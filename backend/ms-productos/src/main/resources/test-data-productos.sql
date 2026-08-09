
-- =============================================================================
--  SECCIÓN 1 — PRODUCTO VÁLIDO PARA CADA CATEGORÍA (5 registros)
--  Cubre: todas las variantes del enum Categoria, valores típicos
-- =============================================================================

INSERT INTO ms_prd_producto (id, nombre, descripcion, stock_actual, stock_minimo, precio, descuento_promocional, categoria)
VALUES (1,
        'Cemento Portland CPC 50 kg',
        'Cemento Portland normal para construcción civil',
        0,
        50,
        8500.00,
        0,
        'CEMENTOS') ON CONFLICT (id) DO NOTHING;

INSERT INTO ms_prd_producto (id, nombre, descripcion, stock_actual, stock_minimo, precio, descuento_promocional, categoria)
VALUES (2,
        'Placa de Yeso 12.7 mm 1.20 x 2.40 m',
        'Placa de yeso estándar para tabiques interiores',
        0,
        30,
        4200.00,
        500.00,
        'PLACAS') ON CONFLICT (id) DO NOTHING;

INSERT INTO ms_prd_producto (id, nombre, descripcion, stock_actual, stock_minimo, precio, descuento_promocional, categoria)
VALUES (3,
        'Perfil Metálico U 34x34x3000 mm',
        'Perfil metálico galvanizado para estructura de tabiques',
        0,
        100,
        650.00,
        0,
        'PERFILES') ON CONFLICT (id) DO NOTHING;

INSERT INTO ms_prd_producto (id, nombre, descripcion, stock_actual, stock_minimo, precio, descuento_promocional, categoria)
VALUES (4,
        'Mortero Pegamento Cerámico 25 kg',
        'Mortero adhesivo para colocación de cerámicos',
        0,
        20,
        1800.00,
        0,
        'MORTEROS') ON CONFLICT (id) DO NOTHING;

INSERT INTO ms_prd_producto (id, nombre, descripcion, stock_actual, stock_minimo, precio, descuento_promocional, categoria)
VALUES (5,
        'Yeso Premium 40 kg',
        'Yeso fino para terminaciones interiores',
        0,
        15,
        2200.00,
        0,
        'YESERIA') ON CONFLICT (id) DO NOTHING;

-- =============================================================================
--  SECCIÓN 2 — VALORES LÍMITE PARA PRECIO
--  Cubre: mínimo positivo, máximo razonable, valor con muchos decimales
-- =============================================================================

-- 2a. Precio mínimo positivo (0.01 → $0.01)
INSERT INTO ms_prd_producto (id, nombre, descripcion, stock_actual, stock_minimo, precio, descuento_promocional, categoria)
VALUES (10,
        'Clavo para madera 2" x 50u',
        'Clavo de acero para madera',
        0,
        100,
        0.01,
        0,
        'PERFILES') ON CONFLICT (id) DO NOTHING;

-- 2b. Precio alto (≈ $10M)
INSERT INTO ms_prd_producto (id, nombre, descripcion, stock_actual, stock_minimo, precio, descuento_promocional, categoria)
VALUES (11,
        'Viga IPN 200 x 12 m - Acero',
        'Viga de acero laminado para estructuras',
        0,
        2,
        99999999.99,
        0,
        'PERFILES') ON CONFLICT (id) DO NOTHING;

-- 2c. Precio con alta precisión decimal
INSERT INTO ms_prd_producto (id, nombre, descripcion, stock_actual, stock_minimo, precio, descuento_promocional, categoria)
VALUES (12,
        'Tornillo Autoperforante 1/2" x 100u',
        'Tornillo autoperforante con punta broca',
        0,
        200,
        0.123456789,
        0,
        'PERFILES') ON CONFLICT (id) DO NOTHING;

-- 2d. Precio con valor peligroso: igual que el descuento (10000 - 10000 = 0)
INSERT INTO ms_prd_producto (id, nombre, descripcion, stock_actual, stock_minimo, precio, descuento_promocional, categoria)
VALUES (13,
        'Ladrillo Hueco 18x18x33 Cerámico x 1u',
        'Ladrillo cerámico para mampostería',
        0,
        500,
        10000.00,
        10000.00,
        'MORTEROS') ON CONFLICT (id) DO NOTHING;

-- =============================================================================
--  SECCIÓN 4 — VALORES LÍMITE PARA STOCK_ACTUAL
--  Cubre: 0, 1, valor grande, valor negativo (borde)
--  NOTA: saveProducto() fuerza stock_actual = 0, pero en BD se pueden insertar
--        valores arbitrarios para simular estados posteriores a provisiones.
-- =============================================================================

-- 4a. Stock actual en 0 (recién creado, sin provisionar)
INSERT INTO ms_prd_producto (id, nombre, descripcion, stock_actual, stock_minimo, precio, descuento_promocional, categoria)
VALUES (30,
        'Pegamento Cerámico 20 kg - Sin stock',
        'Producto nuevo sin stock inicial',
        0,
        50,
        1200.00,
        0,
        'MORTEROS') ON CONFLICT (id) DO NOTHING;

-- 4b. Stock actual en 1 (mínimo con unidade disponible)
INSERT INTO ms_prd_producto (id, nombre, descripcion, stock_actual, stock_minimo, precio, descuento_promocional, categoria)
VALUES (31,
        'Cinta de Empapelar 50 m - Stock mínimo',
        'Rollo de cinta para juntas de placas de yeso',
        1,
        5,
        180.00,
        0,
        'YESERIA') ON CONFLICT (id) DO NOTHING;

-- 4c. Stock actual alto
INSERT INTO ms_prd_producto (id, nombre, descripcion, stock_actual, stock_minimo, precio, descuento_promocional, categoria)
VALUES (32,
        'Arena Fina x 50 kg - Alto stock',
        'Arena fina para preparación de morteros',
        50000,
        1000,
        250.00,
        0,
        'MORTEROS') ON CONFLICT (id) DO NOTHING;

-- 4d. Stock actual igual a Integer.MAX_VALUE (≈ 2.1B)
INSERT INTO ms_prd_producto (id, nombre, descripcion, stock_actual, stock_minimo, precio, descuento_promocional, categoria)
VALUES (33,
        'Agua Destilada 1L - Stock virtual infinito',
        'Agua destilada para preparación',
        2147483647,
        10000,
        80.00,
        0,
        'MORTEROS') ON CONFLICT (id) DO NOTHING;

-- =============================================================================
--  SECCIÓN 5 — VALORES LÍMITE PARA STOCK_MINIMO
--  Cubre: 0, 1, mayor que stock_actual, igual a stock_actual, valor grande
-- =============================================================================

-- 5a. Stock mínimo = 0 (sin alerta de reposición)
INSERT INTO ms_prd_producto (id, nombre, descripcion, stock_actual, stock_minimo, precio, descuento_promocional, categoria)
VALUES (40,
        'Pintura Látex Blanco 20L - Sin mínimo',
        'Pintura al látex para interiores',
        10,
        0,
        3500.00,
        0,
        'YESERIA') ON CONFLICT (id) DO NOTHING;

-- 5b. Stock mínimo = 1 (mínimo positivo)
INSERT INTO ms_prd_producto (id, nombre, descripcion, stock_actual, stock_minimo, precio, descuento_promocional, categoria)
VALUES (41,
        'Rodillo de Pete 23 cm',
        'Rodillo para pintura',
        50,
        1,
        450.00,
        0,
        'YESERIA') ON CONFLICT (id) DO NOTHING;

-- 5c. Stock mínimo mayor que stock actual → necesidad de reposición
INSERT INTO ms_prd_producto (id, nombre, descripcion, stock_actual, stock_minimo, precio, descuento_promocional, categoria)
VALUES (42,
        'Disco de Corte 4 1/2" - Stock crítico',
        'Disco de corte para amoladora',
        3,
        50,
        350.00,
        0,
        'PERFILES') ON CONFLICT (id) DO NOTHING;

-- 5d. Stock mínimo igual a stock actual → en el límite
INSERT INTO ms_prd_producto (id, nombre, descripcion, stock_actual, stock_minimo, precio, descuento_promocional, categoria)
VALUES (43,
        'Guantes de Trabajo Cuero - Stock exacto',
        'Guantes de cuero para construcción',
        25,
        25,
        1200.00,
        0,
        'CEMENTOS') ON CONFLICT (id) DO NOTHING;

-- 5e. Stock mínimo grande (sin riesgo práctico de caer debajo)
INSERT INTO ms_prd_producto (id, nombre, descripcion, stock_actual, stock_minimo, precio, descuento_promocional, categoria)
VALUES (44,
        'Clavo tipo París 1" x kg - Mínimo alto',
        'Clavo tipo parís para fijación de placas',
        10000,
        5000,
        600.00,
        0,
        'PERFILES') ON CONFLICT (id) DO NOTHING;

-- =============================================================================
--  SECCIÓN 6 — VALORES LÍMITE PARA DESCUENTO_PROMOCIONAL
--  Cubre: NULL, 0, positivo < precio, igual a precio, mayor a precio, negativo
-- =============================================================================

-- 6a. Descuento = NULL (se espera que la app use ZERO por defecto)
INSERT INTO ms_prd_producto (id, nombre, descripcion, stock_actual, stock_minimo, precio, descuento_promocional, categoria)
VALUES (50,
        'Masilla Plástica 1 kg - Sin descuento',
        'Masilla para reparación de superficies',
        0,
        20,
        900.00,
        NULL,
        'YESERIA') ON CONFLICT (id) DO NOTHING;

-- 6b. Descuento = 0 (explícito)
INSERT INTO ms_prd_producto (id, nombre, descripcion, stock_actual, stock_minimo, precio, descuento_promocional, categoria)
VALUES (51,
        'Enduido Plástico 2 kg - Descuento cero',
        'Enduido para interiores',
        0,
        15,
        1100.00,
        0,
        'YESERIA') ON CONFLICT (id) DO NOTHING;

-- 6c. Descuento positivo menor que el precio (típico)
INSERT INTO ms_prd_producto (id, nombre, descripcion, stock_actual, stock_minimo, precio, descuento_promocional, categoria)
VALUES (52,
        'Fieltro Asfáltico 10 m² - 15% off',
        'Fieltro asfáltico para cubiertas',
        0,
        30,
        3400.00,
        510.00,
        'PLACAS') ON CONFLICT (id) DO NOTHING;

-- 6d. Descuento igual al precio → precio final = 0 (caso borde)
INSERT INTO ms_prd_producto (id, nombre, descripcion, stock_actual, stock_minimo, precio, descuento_promocional, categoria)
VALUES (53,
        'Muestra Gratis Promocional',
        'Producto promocional sin costo (descuento = precio)',
        0,
        5,
        1000.00,
        1000.00,
        'CEMENTOS') ON CONFLICT (id) DO NOTHING;

-- 6e. Descuento mayor que el precio → precio final negativo (borde)
INSERT INTO ms_prd_producto (id, nombre, descripcion, stock_actual, stock_minimo, precio, descuento_promocional, categoria)
VALUES (54,
        'Cupón de Descuento Extra - Precio negativo',
        'Descuento mayor al precio del producto',
        0,
        1,
        500.00,
        800.00,
        'PLACAS') ON CONFLICT (id) DO NOTHING;

-- 6f. Descuento con valor negativo (inválido semánticamente pero permitido)
INSERT INTO ms_prd_producto (id, nombre, descripcion, stock_actual, stock_minimo, precio, descuento_promocional, categoria)
VALUES (55,
        'Recargo por urgencia - Descuento negativo',
        'Producto con "descuento" negativo (recargo)',
        0,
        10,
        2000.00,
        -500.00,
        'MORTEROS') ON CONFLICT (id) DO NOTHING;

-- =============================================================================
--  SECCIÓN 7 — CASOS BORDE: COMBINACIONES CRÍTICAS
--  Cubre: relaciones entre stock_actual, stock_minimo, precio, descuento
-- =============================================================================

-- 7a. Stock actual = 0 y stock_minimo = 0 (sin producto, sin alerta)
INSERT INTO ms_prd_producto (id, nombre, descripcion, stock_actual, stock_minimo, precio, descuento_promocional, categoria)
VALUES (60,
        'Agotado sin alerta - Todo en 0',
        'Producto sin stock y sin mínimo definido',
        0,
        0,
        5000.00,
        0,
        'CEMENTOS') ON CONFLICT (id) DO NOTHING;

-- 7b. Precio = 0 (gratuito, borde)
INSERT INTO ms_prd_producto (id, nombre, descripcion, stock_actual, stock_minimo, precio, descuento_promocional, categoria)
VALUES (61,
        'Muestra gratis',
        'Producto de cortesía sin costo',
        100,
        10,
        0,
        0,
        'PLACAS') ON CONFLICT (id) DO NOTHING;

-- 7c. Todos los campos obligatorios al mínimo no nulo
INSERT INTO ms_prd_producto (id, nombre, descripcion, stock_actual, stock_minimo, precio, descuento_promocional, categoria)
VALUES (62,
        'Mínimo obligatorio',
        NULL,
        0,
        0,
        0.01,
        0,
        'CEMENTOS') ON CONFLICT (id) DO NOTHING;

-- 7d. Solo nombre y precio (descripcion NULL, descuento NULL, stock en 0)
INSERT INTO ms_prd_producto (id, nombre, descripcion, stock_actual, stock_minimo, precio, descuento_promocional, categoria)
VALUES (63,
        'Codo PVC 45° 110 mm',
        NULL,
        0,
        10,
        320.00,
        NULL,
        'PLACAS') ON CONFLICT (id) DO NOTHING;

-- =============================================================================
--  SECCIÓN 8 — PRODUCTOS PARA PROVISIÓN (UPDATE)
--  Se insertan con stock en 0 y precio base para luego aplicar provisiones
-- =============================================================================

INSERT INTO ms_prd_producto (id, nombre, descripcion, stock_actual, stock_minimo, precio, descuento_promocional, categoria)
VALUES (70,
        'Provisionable - Stock cero inicial',
        'Producto listo para recibir provisiones',
        0,
        30,
        5000.00,
        0,
        'CEMENTOS') ON CONFLICT (id) DO NOTHING;

INSERT INTO ms_prd_producto (id, nombre, descripcion, stock_actual, stock_minimo, precio, descuento_promocional, categoria)
VALUES (71,
        'Provisionable - Con stock previo',
        'Producto con stock existente para sumar más',
        150,
        20,
        3200.00,
        0,
        'PLACAS') ON CONFLICT (id) DO NOTHING;

INSERT INTO ms_prd_producto (id, nombre, descripcion, stock_actual, stock_minimo, precio, descuento_promocional, categoria)
VALUES (72,
        'Tornillo para Durlock 1" x 1000u - Alta rotación',
        'Tornillo para fijación de placas de yeso',
        0,
        500,
        1500.00,
        0,
        'PERFILES') ON CONFLICT (id) DO NOTHING;

-- =============================================================================
--  SECCIÓN 9 — PRODUCTOS PARA CHECK STOCK
--  Se insertan con stocks conocidos para verificar suficiencia
-- =============================================================================

INSERT INTO ms_prd_producto (id, nombre, descripcion, stock_actual, stock_minimo, precio, descuento_promocional, categoria)
VALUES (80,
        'Check - Stock suficiente (1000 disp)',
        'Producto con alto stock para pruebas de verificación',
        1000,
        50,
        100.00,
        0,
        'CEMENTOS') ON CONFLICT (id) DO NOTHING;

INSERT INTO ms_prd_producto (id, nombre, descripcion, stock_actual, stock_minimo, precio, descuento_promocional, categoria)
VALUES (81,
        'Check - Stock exacto (10 disp)',
        'Producto con stock justo para pruebas',
        10,
        10,
        200.00,
        0,
        'PLACAS') ON CONFLICT (id) DO NOTHING;

INSERT INTO ms_prd_producto (id, nombre, descripcion, stock_actual, stock_minimo, precio, descuento_promocional, categoria)
VALUES (82,
        'Check - Stock insuficiente (1 disp)',
        'Producto con stock muy bajo para pruebas',
        1,
        50,
        300.00,
        0,
        'PERFILES') ON CONFLICT (id) DO NOTHING;

INSERT INTO ms_prd_producto (id, nombre, descripcion, stock_actual, stock_minimo, precio, descuento_promocional, categoria)
VALUES (83,
        'Check - Stock cero',
        'Producto sin stock para pruebas',
        0,
        20,
        400.00,
        0,
        'MORTEROS') ON CONFLICT (id) DO NOTHING;

INSERT INTO ms_prd_producto (id, nombre, descripcion, stock_actual, stock_minimo, precio, descuento_promocional, categoria)
VALUES (84,
        'Check - Stock negativo (-5 disp)',
        'Producto con stock negativo (posible error)',
        -5,
        10,
        500.00,
        0,
        'YESERIA') ON CONFLICT (id) DO NOTHING;

-- =============================================================================
--  SECCIÓN 11 — REAJUSTE DE LA SECUENCIA (para evitar conflictos de ID)
-- =============================================================================
SELECT setval('ms_prd_producto_id_seq', COALESCE((SELECT MAX(id) FROM ms_prd_producto), 0) + 1, false);


-- =============================================================================
--  CONSULTAS DE VERIFICACIÓN
--  =============================================================================
--  Descomentar para validar los datos insertados:

-- SELECT COUNT(*) AS total_productos FROM ms_prd_producto;
--
-- SELECT id, nombre, categoria, stock_actual, stock_minimo, precio, descuento_promocional
-- FROM ms_prd_producto
-- ORDER BY id;
--
-- -- Verificar productos agrupados por categoría
-- SELECT categoria, COUNT(*) AS cantidad FROM ms_prd_producto GROUP BY categoria;
--
-- -- Productos con stock crítico (actual < mínimo)
-- SELECT id, nombre, stock_actual, stock_minimo FROM ms_prd_producto WHERE stock_actual < stock_minimo;
--
-- -- Productos con precio gratuito (precio = 0)
-- SELECT id, nombre, precio FROM ms_prd_producto WHERE precio = 0;
--
-- -- Productos con descuento mayor o igual al precio
-- SELECT id, nombre, precio, descuento_promocional FROM ms_prd_producto WHERE descuento_promocional >= precio;
--
-- -- Productos sin descripción
-- SELECT id, nombre FROM ms_prd_producto WHERE descripcion IS NULL;


-- =============================================================================
--  OPERACIONES DE EJEMPLO (PROVISIÓN Y DESCUENTO)
--  =============================================================================
--  Estas consultas simulan las operaciones que hace la API vía servicio Java.

-- ---- PROVISIÓN: Agregar 200 unidades al producto #72 ----
-- UPDATE ms_prd_producto
-- SET stock_actual = stock_actual + 200,
--     precio = 1400.00
-- WHERE id = 72;
--
-- ---- PROVISIÓN: Reponer stock del producto #70 (0 + 500) ----
-- UPDATE ms_prd_producto
-- SET stock_actual = stock_actual + 500,
--     precio = 4800.00
-- WHERE id = 70;
--
-- ---- DESCUENTO: Asignar descuento promocional ----
-- UPDATE ms_prd_producto
-- SET descuento_promocional = 1000.00
-- WHERE id = 3;
--
-- ---- DESCUENTO: Quitar descuento (set a 0) ----
-- UPDATE ms_prd_producto
-- SET descuento_promocional = 0
-- WHERE id = 2;
--
-- ---- CHECK STOCK: Consulta auxiliar para saber si hay suficiente ----
-- --  (Equivalente a lo que hace checkStock() en Java)
-- SELECT CASE
--     WHEN stock_actual >= 50 THEN true
--     ELSE false
-- END AS stock_suficiente
-- FROM ms_prd_producto WHERE id = 80;
