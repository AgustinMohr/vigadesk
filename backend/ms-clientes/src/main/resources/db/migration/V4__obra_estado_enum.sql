-- Hibernate 6 mapea EstadoObra (@Enumerated STRING) a una columna ENUM de MySQL.
-- V1 creo ms_cli_obra.estado como VARCHAR, asi que una base nueva no pasaba la validacion
-- del esquema (ddl-auto=validate). Es idempotente: si la columna ya es ENUM no cambia nada.
ALTER TABLE ms_cli_obra
    MODIFY COLUMN estado ENUM('HABILITADA', 'PENDIENTE', 'FINALIZADA') NULL;
