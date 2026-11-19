-- V1 creo las tablas en MAYUSCULAS, pero Hibernate (naming strategy de Spring) las consulta
-- en minuscula. En MySQL sobre Linux los nombres de tabla distinguen mayusculas, asi que una
-- base nueva no pasaba la validacion del esquema (ddl-auto=validate).
-- Renombra cada tabla solo si existe con mayusculas; en bases ya en minuscula no hace nada.
-- Los indices y las claves foraneas siguen a la tabla renombrada.

SET @rename_cliente = (SELECT IF(COUNT(*) > 0, 'RENAME TABLE MS_CLI_CLIENTE TO ms_cli_cliente', 'DO 0')
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND BINARY table_name = 'MS_CLI_CLIENTE');
PREPARE stmt FROM @rename_cliente;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @rename_obra = (SELECT IF(COUNT(*) > 0, 'RENAME TABLE MS_CLI_OBRA TO ms_cli_obra', 'DO 0')
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND BINARY table_name = 'MS_CLI_OBRA');
PREPARE stmt FROM @rename_obra;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @rename_usuario = (SELECT IF(COUNT(*) > 0, 'RENAME TABLE MS_CLI_USUARIO TO ms_cli_usuario', 'DO 0')
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND BINARY table_name = 'MS_CLI_USUARIO');
PREPARE stmt FROM @rename_usuario;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @rename_pago = (SELECT IF(COUNT(*) > 0, 'RENAME TABLE MS_CLI_PAGO TO ms_cli_pago', 'DO 0')
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND BINARY table_name = 'MS_CLI_PAGO');
PREPARE stmt FROM @rename_pago;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
