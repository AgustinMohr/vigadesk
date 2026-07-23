# tp-dan-2024

Microservices system (clientes, productos, pedidos) with Next.js frontend, Spring Gateway, and HAProxy.

## Architecture

```
Browser -> HAProxy:80 -> Next.js:3000 -> Spring Gateway:8080 -> microservicios
                                              ↓                      ↓
                                         Eureka :8761        RabbitMQ, MySQL,
                                                             PostgreSQL, MongoDB
```

## Repo layout

| Directory | Role | Stack | Port |
|---|---|---|---|
| `dan-eureka-srv/` | Service discovery | SB 3.2.5, Java 21 | 8761 |
| `dan-gateway/` | API gateway | SB 3.2.3, Java 17 | 3080 |
| `ms-clientes/` | Clientes CRUD | SB 3.2.5, Java 21, MySQL+JPA | 6080 |
| `ms-productos/` | Productos CRUD | SB 3.2.6, Java 21, PostgreSQL+JPA, RabbitMQ | 6180 |
| `ms-pedidos/` | Pedidos CRUD | SB 3.2.6, Java 21, MongoDB, RabbitMQ | 6280 |
| `frontend/` | Next.js UI | Next 16, React 19, Tailwind v4 | 3000 |
| `haproxy/` | Reverse proxy | HAProxy 3.0 | 80 |

## Commands

**Backend (full stack)** — from root:
```bash
docker network create backend-net   # one-time prerequisite
docker compose -f docker-compose.yml up --build
```

**Frontend + HAProxy** — from root:
```bash
docker compose -f docker-compose.frontend.yml up --build
```

**Single service build without Docker** — inside each `ms-*/` dir:
```bash
./mvnw clean package -DskipTests
```

**Frontend dev** — inside `frontend/`:
```bash
npm run dev       # dev server
npm run build     # production build
npm run lint      # ESLint (includes TS checks via eslint-config-next)
```

## Key wiring

- Every microservice registers in Eureka (`SPRING_EUREKA_DEFAULT_ZONE` env var).
- Gateway routes: `/clientes/**` → `lb://ms-clientes`, `/productos/**` → `lb://ms-productos`, `/pedidos/**` → `lb://MS-PEDIDOS`. Path prefix is stripped via `RewritePath`.
- Frontend proxies `/api/*` → Gateway via `src/app/api/[...path]/route.ts`. Gateway URL is `SPRING_GATEWAY_URL` env var (default `http://localhost:3080`).
- All Docker infra uses `backend-net` external network.

## Database credentials (dev only)

| DB | User | Password | DB name |
|---|---|---|---|
| MySQL | danuser | danpassword | dandb |
| PostgreSQL | pguser | pgpassword | pg-dan |
| MongoDB | root | dan | pedidosdb |

## Notable details

- `next.config.ts` uses `output: "standalone"` (Docker multi-stage build copies `.next/standalone`). `reactCompiler: true` is enabled.
- `ms-clientes` has 3 instance replicas in its local `docker-compose.yml` (set via `SPRING_INSTANCIA`). Root `docker-compose.yml` runs 1 instance.
- RabbitMQ queue `stock-update-queue` is shared between `ms-productos` (sender) and `ms-pedidos` (consumer). Both have identical `RabbitMQConfig`.
- Config is 100% env-var-driven (all `application.properties` use `${SPRING_*}` placeholders).
- `@LogExecutionTime` AOP annotation used in `ms-clientes` and `ms-productos`.
- Prometheus + Actuator metrics enabled in `ms-clientes`, `ms-productos`, and `dan-gateway`.
- Zipkin tracing configured in `ms-productos` (commented out in `ms-clientes`).
- `ms-clientes` test profile uses H2 in-memory DB by default; has `application-db.properties` for Testcontainers/MySQL.
- Testcontainers used for integration tests in `ms-productos` (MongoDB, RabbitMQ) and `ms-pedidos` (MongoDB, RabbitMQ).
- Lombok (`@Data`) used across all Java models.
- `ms-clientes` uses Logback GELF for Graylog log shipping.
- OAuth2/Spring Security deps in `dan-gateway` are **commented out** — not active.
- No CI workflows exist (empty `.github/`).

## Port map

| Service | Internal | Host |
|---|---|---|
| Eureka | 8761 | 8761 |
| Gateway | 8080 | 3080 |
| ms-clientes | 8080 | 6080 |
| ms-productos | 8080 | 6180 |
| ms-pedidos | 8080 | 6280 |
| Frontend | 3000 | (via HAProxy :80) |
| HAProxy | 80 | 80 |
| MySQL | 3306 | 3306 |
| PostgreSQL | 5432 | 5432 |
| MongoDB | 27017 | 27017 |
| RabbitMQ | 5672/15672 | 5672/15672 |
