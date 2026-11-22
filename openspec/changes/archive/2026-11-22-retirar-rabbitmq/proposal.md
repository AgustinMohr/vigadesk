# Retirar RabbitMQ (R2.12, opcion A)

Estado: aplicado el 2026-11-22. Decidido por el equipo del proyecto entre dos salidas.

## Contexto

`ms-pedidos` declaraba las colas `stock-update-queue` y `stock-return-queue` y `ms-productos` las
consumia con `@RabbitListener` (mensaje de texto `idProducto;cantidad`). Ningun codigo publicaba en
esas colas: la reserva y la liberacion de stock de un pedido ya eran llamadas Feign sincronas
(`PUT /api/productos/stock/reserve` y `stock/release`).

Los listeners descontaban y devolvian stock sin idempotencia ni control de mensajes duplicados.
Cualquiera con acceso al broker podia alterar el stock por un camino distinto al de la API, sin
auditoria de pedido y sin la proteccion de la reserva atomica.

## Opciones

- **A. Retirar RabbitMQ.** Elimina colas, listeners, configuracion, dependencia y el servicio del
  compose. El stock queda como flujo sincrono.
- **B. Mantener RabbitMQ para eventos de dominio** (por ejemplo notificaciones, FT-09), con mensaje JSON,
  `messageId`, cola de mensajes fallidos y consumidor idempotente.

## Decision

**A.** No hay ningun consumidor real de eventos hoy; mantener el broker sumaba un componente que operar
(memoria, credenciales, rotacion de secretos, arranque) y un camino de escritura sin proteger.
Construir B sin un caso de uso concreto era diseno especulativo.

## Cambios

- `ms-productos`: se eliminan `handleStockUpdate`, `handleStockReturn`, el parseo del mensaje, `RabbitMQConfig`,
  la dependencia `spring-boot-starter-amqp` y las propiedades `spring.rabbitmq.*`, con sus tests.
- `ms-pedidos`: se elimina `RabbitMQConfig`, la dependencia y las propiedades.
- `ms-clientes`: se eliminan las propiedades `spring.rabbitmq.*` (no usaba el broker).
- `docker-compose.yml`, `docker-compose.prod.yml` y los compose por modulo: se elimina el servicio
  `rabbitmq`, su volumen, las variables `SPRING_RABBITMQ_*` y las dependencias de arranque.
- `.env.example`: se eliminan `RABBITMQ_USERNAME` y `RABBITMQ_PASSWORD`.
- `ops/rotate-secrets.ps1`: pasa de 9 a 8 pasos, sin la rotacion de RabbitMQ.
- README, `AGENTS.md` y `comandos e ips.txt` quedan coherentes con la decision.

## Consecuencias

- Los nombres de cola dejan de ser contrato intocable.
- Instalaciones existentes: el contenedor y el volumen `rabbitmq-data` quedan huerfanos y se pueden borrar
  (`docker compose down --remove-orphans` y `docker volume rm vigadesk_rabbitmq-data`).
- Si FT-09 (notificaciones por eventos, R4.8) lo necesita, se incorpora un broker de nuevo con el diseno de la
  opcion B y pruebas con RabbitMQ real y mensaje duplicado.
