# tp-dan-2024 — Sistema de Gestión de Pedidos

Sistema de microservicios para la gestión de clientes, productos y pedidos, con frontend en Next.js, API Gateway con Spring Cloud Gateway, service discovery con Eureka, y reverse proxy con HAProxy.

---

## 1. Arquitectura general

```
Browser
  │
  ▼ :80
HAProxy ──────────────────────────────────┐
  │                                       │
  │ /api/*                                │ default
  ▼                                       ▼
Spring Gateway :8080              Next.js :3000
  │                                       │
  │ lb:// (Eureka)                        │ SSR fetch
  ▼                                       ▼
  ┌─────────────────┐         ┌─────────────────────┐
  │   ms-clientes   │ ──►     │   ms-productos      │
  │   Spring 3.2.5  │         │   Spring 3.2.6      │
  │   MySQL + JPA   │         │   PostgreSQL + JPA  │
  │   Puerto 6080   │         │   Puerto 6180       │
  └────────┬────────┘         └──────────┬──────────┘
           │                             │
           │              ┌──────────────┴──────────────┐
           │              │         RabbitMQ             │
           │              │  stock-update-queue          │
           │              │  stock-return-queue          │
           │              └──────────────┬──────────────┘
           │                             │
           │              ┌──────────────┴──────────┐
           │              │      ms-pedidos          │
           │              │   Spring 3.2.6           │
           │              │   MongoDB                │
           │              │   Puerto 6280            │
           │              └─────────────────────────┘
           │
  ┌────────┴────────┐
  │   Eureka :8761  │
  └─────────────────┘
```

Cada microservicio se registra en Eureka al arrancar. El Gateway resuelve las rutas mediante `lb://` (load balancing sobre instancias registradas). La comunicación síncrona entre servicios usa Feign Clients. La comunicación asíncrona usa RabbitMQ.

---

## 2. Infraestructura (Docker)

### Redes

Todos los servicios comparten la red externa `backend-net`, creada manualmente:

```bash
docker network create backend-net
```

### Composes

| Archivo | Contenido |
|---|---|
| `docker-compose.yml` | Backend completo: Eureka, Gateway, 3 microservicios, 3 bases de datos, RabbitMQ |
| `docker-compose.frontend.yml` | HAProxy + Next.js |
| `dan-eureka-srv/docker-compose.yml` | Eureka standalone |
| `dan-gateway/docker-compose.yml` | Gateway standalone |
| `ms-clientes/docker-compose.yml` | ms-clientes + MySQL |
| `ms-productos/docker-compose.yml` | ms-productos + PostgreSQL |
| `ms-pedidos/docker-compose.yml` | ms-pedidos + MongoDB |

### Dockerfiles

**Java (todos los microservicios):** 3 etapas
1. `maven:3.9.6-eclipse-temurin-17` — descarga dependencias (`dependency:go-offline`)
2. `maven:3.9.6-eclipse-temurin-17` — compila JAR (`mvn clean package -DskipTests`)
3. `eclipse-temurin:17-jre-alpine` — ejecuta el JAR

**Frontend:** 3 etapas
1. `node:22-alpine` — `npm ci`
2. `node:22-alpine` — `npm run build`
3. `node:22-alpine` — ejecuta `server.js` desde `.next/standalone`

### Healthchecks

Cada base de datos y RabbitMQ tiene healthchecks que los servicios esperan antes de arrancar mediante `depends_on` + `condition: service_healthy`.

---

## 3. Service Discovery (Eureka)

`dan-eureka-srv` en puerto **8761**. Servidor standalone (`registerWithEureka=false`).

```properties
eureka.instance.hostname=${SPRING_EUREKA_HOST_NAME}
eureka.client.serviceUrl.defaultZone=${SPRING_EUREKA_DEFAULT_ZONE}
```

Todos los microservicios se registran con:
```properties
eureka.client.serviceUrl.defaultZone=http://ms-eureka:8761/eureka/
eureka.instance.leaseRenewalIntervalInSeconds=5
eureka.instance.leaseExpirationDurationInSeconds=5
```

---

## 4. API Gateway (Spring Cloud Gateway)

`dan-gateway` en puerto **3080** (host) → **8080** (container).

### Rutas configuradas

| Ruta externa | URI (Eureka LB) | RewritePath |
|---|---|---|
| `/clientes/**`, `/api/clientes/**`, `/api/obras/**` | `lb://ms-clientes` | `/clientes/(.*) → /$1` |
| `/productos/**`, `/api/productos/**`, `/api/categorias/**` | `lb://ms-productos` | `/productos/(.*) → /$1` |
| `/pedidos/**`, `/api/pedidos/**` | `lb://ms-pedidos` | `/pedidos/(.*) → /$1` |

El Gateway realiza **load balancing** contra las instancias registradas en Eureka. La compresión GZIP está habilitada globalmente.

---

## 5. HAProxy

Escucha en puerto **80**. Routing:

- `/api/*` → backend `gateway_backend` (Spring Gateway :8080)
- Cualquier otra ruta → backend `next_frontend` (Next.js :3000)

Healthchecks en ambos backends.

---

## 6. Microservicios

### 6.1 ms-clientes

| Atributo | Valor |
|---|---|
| Framework | Spring Boot 3.2.5 |
| Java | 21 |
| Base de datos | MySQL 8.0 — `dandb` |
| Tablas | `MS_CLI_CLIENTE`, `MS_CLI_OBRA` |
| Puerto | 6080 (host) : 8080 (container) |

**Modelos:**

```java
// Cliente
id: Integer, nombre: String, correoElectronico: String,
cuit: String, maximoDescubierto: BigDecimal, maxObrasActivas: Integer

// Obra
id: Integer, direccion: String, lat: float, lng: float,
cliente: @ManyToOne, presupuesto: BigDecimal,
estado: EstadoObra (HABILITADA | PENDIENTE | FINALIZADA)
```

**Endpoints:**

| Método | Path | Descripción |
|---|---|---|
| GET | `/api/clientes` | Listar todos |
| GET | `/api/clientes/{id}` | Obtener por ID |
| POST | `/api/clientes` | Crear |
| PUT | `/api/clientes/{id}` | Actualizar |
| DELETE | `/api/clientes/{id}` | Eliminar |
| GET | `/api/clientes/{id}/saldo?monto=X` | Verificar si monto ≤ maximoDescubierto |
| GET | `/api/clientes/{id}/maximo-descubierto` | Obtener máximo descubierto |
| GET | `/api/obras` | Listar obras |
| GET | `/api/obras/sin-asignar` | Obras sin cliente |
| GET | `/api/obras/by-cliente/{id}` | Obras de un cliente |
| POST | `/api/obras` | Crear obra |
| PUT | `/api/obras/{id}/asignar-cliente/{clienteId}` | Asignar obra a cliente |
| PUT | `/api/obras/{id}/estado` | Cambiar estado |

**Lógica de negocio:**
- Al asignar una obra a un cliente, se verifica que no supere `maxObrasActivas`
- `checkSaldo` compara el monto acumulado de pedidos contra `maximoDescubierto`
- `@LogExecutionTime` via AOP registra tiempos de ejecución en logs

### 6.2 ms-productos

| Atributo | Valor |
|---|---|
| Framework | Spring Boot 3.2.6 |
| Java | 21 |
| Base de datos | PostgreSQL 17 — `pg-dan` |
| Tabla | `ms_prd_producto` (schema vía `schema-productos.sql`) |
| Puerto | 6180 (host) : 8080 (container) |

**Modelos:**

```java
// Producto
id: Long, codigo: String, nombre: String, descripcion: String,
stockActual: int, stockMinimo: int, precio: BigDecimal,
descuentoPromocional: BigDecimal, categoria: Categoria (enum)

// Categoria: CEMENTOS | PLACAS | PERFILES | MORTEROS | YESERIA
```

**Endpoints:**

| Método | Path | Descripción |
|---|---|---|
| GET | `/api/productos` | Listar todos |
| GET | `/api/productos/{id}` | Obtener por ID |
| POST | `/api/productos` | Crear |
| PUT | `/api/productos/{id}` | Actualizar |
| DELETE | `/api/productos/{id}` | Eliminar |
| GET | `/api/productos/search` | Buscar por nombre, código, precio, stock |
| PUT | `/api/productos/stock/check` | Verificar disponibilidad de stock |
| PUT | `/api/productos/provision` | Procesar provisión de stock |
| PUT | `/api/productos/{id}/descuento` | Aplicar descuento promocional |
| GET | `/api/categorias` | Listar categorías |

**RabbitMQ:** Envía mensajes a `stock-update-queue` con formato `"{idProducto};{cantidad}"` al procesar una provisión.

### 6.3 ms-pedidos

| Atributo | Valor |
|---|---|
| Framework | Spring Boot 3.2.6 |
| Java | 21 |
| Base de datos | MongoDB — `pedidosdb` |
| Colección | `pedidos` |
| Puerto | 6280 (host) : 8080 (container) |

**Modelos:**

```java
// Pedido
id: String (MongoDB _id), fecha: Instant, numeroPedido: Integer,
usuario: String, observaciones: String,
cliente: Cliente (embebido), obra: Obra (embebido),
total: BigDecimal, estado: EstadoPedido,
detalle: List<DetallePedido>

// DetallePedido
producto: Producto (embebido), cantidad: Integer,
precioUnitario: BigDecimal, descuento: BigDecimal,
precioFinal: BigDecimal

// EstadoPedido: RECIBIDO → ACEPTADO → EN_PREPARACION → ENTREGADO
//                                       ↘ CANCELADO / RECHAZADO
```

**Endpoints:**

| Método | Path | Descripción |
|---|---|---|
| GET | `/api/pedidos` | Listar todos |
| GET | `/api/pedidos/search` | Buscar por cliente/estado |
| GET | `/api/pedidos/{id}` | Obtener por ID |
| POST | `/api/pedidos` | Crear |
| PATCH | `/api/pedidos/{id}/estado` | Actualizar estado (ENTREGADO | CANCELADO) |
| DELETE | `/api/pedidos/{id}` | Eliminar |

**Feign Clients:**

```java
// Comunicación con ms-clientes
@FeignClient(name = "ms-clientes")
interface ClienteFeignClient {
    @GetMapping("/api/clientes/{id}/maximo-descubierto")
    BigDecimal getMaximoDescubierto(@PathVariable Integer id);

    @GetMapping("/api/clientes/{id}/saldo")
    Boolean checkSaldo(@PathVariable Integer id, @RequestParam BigDecimal monto);
}

// Comunicación con ms-productos
@FeignClient(name = "ms-productos")
interface ProductoFeignClient {
    @PutMapping("/api/productos/stock/check")
    Boolean checkStock(@RequestBody List<StockUpdateDTO> items);
}
```

**Flujo de creación de pedido:**

```
POST /api/pedidos
  │
  ├─ 1. Calcular total = Σ(producto.precio × cantidad)
  │
  ├─ 2. Consultar maximoDescubierto del cliente (Feign → ms-clientes)
  │     └─ Si error → RECIBIDO (pendiente de validación)
  │
  ├─ 3. Sumar totales de pedidos ACEPTADOS + EN_PREPARACION del cliente
  │
  ├─ 4. Verificar saldo: (gastado + nuevoTotal) ≤ maximoDescubierto
  │     └─ Si supera → RECHAZADO
  │
  ├─ 5. Si ok → ACEPTADO
  │
  ├─ 6. Verificar stock (Feign → ms-productos)
  │     └─ Si hay stock → EN_PREPARACION y enviar mensaje a stock-update-queue
  │     └─ Si no → queda en ACEPTADO
  │
  └─ 7. Guardar en MongoDB
```

**Manejo de cancelación:** Al cancelar un pedido (PATCH a ENTREGADO o CANCELADO), se envía un mensaje a `stock-return-queue` para reponer el stock de cada producto.

---

## 7. Comunicación entre servicios

| Tipo | Mecanismo | Origen → Destino | Propósito |
|---|---|---|---|
| Síncrono | Feign Client + Eureka | ms-pedidos → ms-clientes | Consultar máximo descubierto y saldo |
| Síncrono | Feign Client + Eureka | ms-pedidos → ms-productos | Verificar disponibilidad de stock |
| Asíncrono | RabbitMQ (AMQP) | ms-productos → `stock-update-queue` | Notificar actualización de stock |
| Asíncrono | RabbitMQ (AMQP) | ms-pedidos → `stock-update-queue` | Descontar stock al crear pedido |
| Asíncrono | RabbitMQ (AMQP) | ms-pedidos → `stock-return-queue` | Reponer stock al cancelar pedido |

### RabbitMQ

Configuración idéntica en `ms-productos` y `ms-pedidos`:

```java
@Configuration
public class RabbitMQConfig {
    public static final String STOCK_UPDATE_QUEUE = "stock-update-queue";
    public static final String STOCK_RETURN_QUEUE = "stock-return-queue";

    @Bean
    public Queue stockUpdateQueue() { return new Queue(STOCK_UPDATE_QUEUE, true); }

    @Bean
    public Queue stockReturnQueue() { return new Queue(STOCK_RETURN_QUEUE, true); }
}
```

Los mensajes tienen formato `"{idProducto};{cantidad}"` (ej: `"42;5"`).

---

## 8. Frontend (Next.js 16 + React 19 + Tailwind v4)

### Estructura del proyecto

```
frontend/src/
├── app/
│   ├── layout.tsx              # Layout raíz (fonts, metadata)
│   ├── page.tsx                # Redirige a /dashboard
│   ├── login/page.tsx          # Página de login
│   ├── (app)/
│   │   ├── layout.tsx          # Layout interno (AuthLayout wrapper)
│   │   ├── AuthLayout.tsx      # AuthProvider + AuthGuard + Navbar + Sidebar
│   │   ├── dashboard/          # Dashboard con resumen + stock bajo
│   │   ├── clientes/           # CRUD clientes
│   │   ├── productos/          # CRUD productos
│   │   └── pedidos/            # CRUD pedidos
│   └── api/[...path]/route.ts  # Proxy → Spring Gateway
├── components/                 # Button, Tablas, Modales, Navbar, Sidebar
├── services/                   # http.ts, clienteService, productoService, pedidoService, obraService
├── hooks/                      # useSort
└── lib/
    └── auth/                   # AuthContext, types, testUsers
```

### Proxy API

`frontend/src/app/api/[...path]/route.ts` captura todas las rutas `/api/*` y las reenvía al Spring Gateway:

| Ruta frontend | Ruta gateway |
|---|---|
| `/api/clientes/*` | `/clientes/api/clientes/*` |
| `/api/obras/*` | `/clientes/api/obras/*` |
| `/api/productos/*` | `/productos/api/productos/*` |
| `/api/categorias/*` | `/productos/api/categorias/*` |
| `/api/pedidos/*` | `/pedidos/api/pedidos/*` |

La URL base se configura via `SPRING_GATEWAY_URL` (default `http://localhost:3080`). El Server-Side Rendering usa `SSR_FETCH_URL` (default `http://haproxy`) para que las peticiones SSR no pasen por HAProxy y eviten ciclos.

### Autenticación

Sistema de autenticación client-side usando React Context + localStorage. Dos roles:

| Rol | Permisos |
|---|---|
| ADMIN | CRUD completo en todas las entidades |
| GUEST | Solo lectura (Ver detalle, navegar tablas) |

**Usuarios de prueba:**

| Email | Contraseña | Rol |
|---|---|---|
| `admin@example.com` | `admin123` | ADMIN |
| `guest@example.com` | `guest123` | GUEST |

El `AuthGuard` en `AuthLayout.tsx` redirige a `/login` si no hay sesión. Los botones de crear, editar, eliminar y acciones especiales se ocultan condicionalmente con `{isAdmin && <Button>}`.

---

## 9. Bases de datos

| Servicio | Motor | Puerto | Base de datos | Usuario | Password |
|---|---|---|---|---|---|
| ms-clientes | MySQL 8.0 | 3306 | `dandb` | `danuser` | `danpassword` |
| ms-productos | PostgreSQL 17 | 5432 | `pg-dan` | `pguser` | `pgpassword` |
| ms-pedidos | MongoDB | 27017 | `pedidosdb` | `root` | `dan` |

### Esquemas

**MySQL (ms-clientes):** Tablas `MS_CLI_CLIENTE` y `MS_CLI_OBRA` creadas por JPA (`ddl-auto: update`).

**PostgreSQL (ms-productos):** Tabla `ms_prd_producto` creada por `schema-productos.sql`. Datos de prueba insertados por `test-data-productos.sql`.

**MongoDB (ms-pedidos):** Colección `pedidos` sin esquema fijo. Inicialización vía `mongo-init-pedidos.js`.

---

## 10. Monitoreo y métricas

| Servicio | Prometheus | Actuator | Graylog | Zipkin | AOP |
|---|---|---|---|---|---|
| ms-clientes | ✔ | ✔ | ✔ (GELF) | ✗ (comentado) | ✔ `@LogExecutionTime` |
| ms-productos | ✔ | ✔ | ✔ (GELF) | ✔ | ✔ `@LogExecutionTime` |
| ms-pedidos | ✔ | ✔ | ✔ (GELF) | ✗ | ✗ |
| dan-gateway | ✔ | ✔ | ✗ | ✗ | ✗ |

Endpoints expuestos: `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus`.

---

## 11. Testing

| Servicio | Framework | Base de datos test | Containers | Cobertura |
|---|---|---|---|---|
| ms-clientes | JUnit 5 + Testcontainers | H2 (default), MySQL (testcontainers) | MySQL | JaCoCo ≥ 80% |
| ms-productos | JUnit 5 + Testcontainers | H2 (default) | PostgreSQL, RabbitMQ | JaCoCo ≥ 80% |
| ms-pedidos | JUnit 5 + Testcontainers | MongoDB (testcontainers) | MongoDB, RabbitMQ | JaCoCo ≥ 80% |

El perfil de test deshabilita Eureka (`eureka.client.enabled=false`) y RabbitMQ listener (`spring.rabbitmq.listener.simple.auto-startup=false`).

---

## 12. Cómo ejecutar

### Prerequisito único

```bash
docker network create backend-net
```

### Backend completo

```bash
docker compose -f docker-compose.yml up --build
```

### Frontend + HAProxy

(Requiere el backend corriendo)

```bash
docker compose -f docker-compose.frontend.yml up --build
```

### Servicio individual sin Docker

```bash
cd ms-clientes   # o ms-productos, ms-pedidos, dan-eureka-srv, dan-gateway
./mvnw clean package -DskipTests
java -jar target/*.jar
```

### Frontend en modo desarrollo

```bash
cd frontend
npm run dev
```

---

## 13. Variables de entorno

Cada microservicio usa placeholders `${SPRING_*}` en `application.properties`. No hay valores default en los properties — todo se configura por entorno.

| Variable | Servicio | Propósito |
|---|---|---|
| `SPRING_DATASOURCE_URL` | ms-clientes, ms-productos | URL JDBC de la base de datos |
| `SPRING_DATASOURCE_USERNAME` | ms-clientes, ms-productos | Usuario de base de datos |
| `SPRING_DATASOURCE_PASSWORD` | ms-clientes, ms-productos | Password de base de datos |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | ms-clientes | Estrategia DDL (update) |
| `SPRING_JPA_DATABASE_PLATFORM` | ms-clientes, ms-productos | Dialect Hibernate |
| `SPRING_DATA_MONGODB_URL` | ms-pedidos | URI de conexión MongoDB |
| `SPRING_RABBITMQ_HOST` | ms-clientes, ms-productos, ms-pedidos | Host RabbitMQ |
| `SPRING_RABBITMQ_PORT` | ms-clientes, ms-productos, ms-pedidos | Puerto RabbitMQ |
| `SPRING_EUREKA_DEFAULT_ZONE` | Todos | URL de Eureka server |
| `SPRING_EUREKA_HOST_NAME` | dan-eureka-srv | Hostname de Eureka |
| `SPRING_EUREKA_PORT` | dan-eureka-srv | Puerto de Eureka |
| `SPRING_GATEWAY_URL` | frontend | URL base del Gateway para el proxy |
| `SSR_FETCH_URL` | frontend | URL base para SSR fetches |
| `JAVA_TOOL_OPTIONS` | Todos | Memory limits (`-Xms`, `-Xmx`) |

---

## 14. Puertos expuestos

| Servicio | Puerto interno | Puerto host |
|---|---|---|
| HAProxy | 80 | 80 |
| Next.js | 3000 | — (via HAProxy) |
| Spring Gateway | 8080 | 3080 |
| Eureka | 8761 | 8761 |
| ms-clientes | 8080 | 6080 |
| ms-productos | 8080 | 6180 |
| ms-pedidos | 8080 | 6280 |
| MySQL | 3306 | 3306 |
| PostgreSQL | 5432 | 5432 |
| MongoDB | 27017 | 27017 |
| RabbitMQ (AMQP) | 5672 | 5672 |
| RabbitMQ (Management) | 15672 | 15672 |
