# tp-dan-2024

Sistema de microservicios con Spring Boot, Next.js y HAProxy. Arquitectura orientada a microservicios con descubrimiento Eureka, API Gateway Spring Cloud Gateway, frontend SSR en Next.js y reverse proxy HAProxy.

---

## Arquitectura general

```
                     ┌───────────────────────────────────────┐
                     │            HAPROXY :80                │
                     │  haproxy/haproxy.cfg                  │
                     │                                       │
                     │  ¿path_beg /api/?                     │
                     │       │                               │
                     │       ├── Sí ──▶ backend gateway_backend
                     │       │          ms-gateway-svc:8080  │
                     │       │                               │
                     │       └── No ──▶ backend next_frontend
                     │                   frontend-ui:3000    │
                     └────────────┬─────────────────┬────────┘
                                  │                 │
                          ┌───────▼──────┐         │
                          │  Next.js     │         │
                          │  :3000       │         │
                          │              │         │
                          │ Server Comp. │         │
                          │ SSR fetch vía│         │
                          │ HAProxy      │         │
                          │              │         │
                          └───────┬──────┘         │
                                  │                │
                                  │  SSR fetch:    │
                                  │  http://haproxy│
                                  │  /api/clientes │
                                  ▼                ▼
                     ┌───────────────────────────────────────┐
                     │       SPRING GATEWAY :3080            │
                     │                                       │
                     │  Routes: /api/**, /clientes/**,       │
                     │          /productos/**, /pedidos/**   │
                     │                                       │
                     │  RewritePath con anclaje ^            │
                     │  + compression gzip                   │
                     └──────────────────┬────────────────────┘
                                        │ lb://
                                ┌───────▼────────┐
                                │  Eureka :8761  │
                                │  Service       │
                                │  Discovery     │
                                └───────┬────────┘
                                        │
                          ┌─────────────┴──────────────┐
                          │     Microservicios + DBs    │
                          │                            │
                          │  ms-clientes  :6080 (MySQL)│
                          │  ms-productos :6180 (PgSQL)│
                          │  ms-pedidos   :6280 (Mongo)│
                          │  RabbitMQ     :5672        │
                          └────────────────────────────┘
```

---

## Estructura del repositorio

| Directorio | Rol | Stack | Puerto host |
|---|---|---|---|
| `haproxy/` | Reverse proxy / entry point | HAProxy 3.0 | 80 |
| `frontend/` | UI server-side rendering | Next 16, React 19, Tailwind v4 | 3000 |
| `dan-gateway/` | API Gateway + Load Balancer | SB 3.2.3, Java 17 | 3080 |
| `dan-eureka-srv/` | Service Discovery | SB 3.2.5, Java 21 | 8761 |
| `ms-clientes/` | Microservicio Clientes | SB 3.2.5, Java 21, MySQL | 6080 |
| `ms-productos/` | Microservicio Productos | SB 3.2.6, Java 21, PostgreSQL | 6180 |
| `ms-pedidos/` | Microservicio Pedidos | SB 3.2.6, Java 21, MongoDB | 6280 |

---

## Flujo de comunicación

El sistema tiene **dos caminos de request** que se separan en HAProxy según la URL:

### Camino A — Páginas UI (no empieza con `/api/`)

El browser navega a `/dashboard`, `/clientes`, etc. HAProxy las reconoce como tráfico que **no** empieza con `/api/` y las envía a Next.js.

```
Browser → HAProxy:80  (no /api/)  →  Next.js:3000
                                          │
                                    Server Component (SSR)
                                          │
                              fetch("http://haproxy/api/clientes")
                                          │  ↓
                              HAProxy detecta /api/ → Gateway
                                          │  ↓
                                    Microservicio → DB
                                          │
                                    HTML con datos renderizados
                                          │
                              ◀── Browser recibe HTML listo
```

Next.js, durante el SSR, hace un fetch a `http://haproxy/api/clientes` usando la variable `SSR_FETCH_URL=http://haproxy`. Esta request **sale por HAProxy** (el tráfico atraviesa el balanceador igual que las API calls del browser), y HAProxy la enruta al Gateway por empezar con `/api/`. **Todo el tráfico pasa por HAProxy.**

Si el backend no está listo aún (primer arranque), el Server Component reintenta hasta 3 veces con pausas de 1.5s. Si aún así falla, el Client Component hace un fetch de respaldo silencioso desde el browser automáticamente.

### Camino B — Llamadas API (empieza con `/api/`)

El frontend (ya renderizado en el browser) necesita datos: hace `fetch("/api/clientes")`. Esta llamada sale desde el browser hacia el origen (`localhost:80`), y **HAProxy** la intercepta porque el path empieza con `/api/`.

```
Browser → HAProxy:80  (/api/)  →  Gateway:3080
                                      │
                              RewritePath
                              (^/clientes/ → /api/...)
                                      │
                              lb://ms-clientes (via Eureka)
                                      │
                              ms-clientes:8080 → MySQL
                                      │
                              ◀── JSON response
```

---

## 1. HAProxy — Punto de entrada

**Archivo:** `haproxy/haproxy.cfg`

```haproxy
frontend http_in
    bind *:80

    acl is_api path_beg /api/
    use_backend gateway_backend if is_api
    default_backend next_frontend

backend next_frontend
    option httpchk GET /
    server frontend-ui frontend-ui:3000 check

backend gateway_backend
    option httpchk GET /actuator/health
    server gateway ms-gateway-svc:8080 check
```

**Lógica de enrutamiento:**
- Si el path comienza con `/api/` → envía al Gateway (`ms-gateway-svc:8080`)
- Cualquier otra cosa (páginas, assets, etc.) → envía a Next.js (`frontend-ui:3000`)
- Ambos backends tienen healthcheck para detectar caídas

---

## 2. Next.js — UI con Server Components

**Archivo:** `frontend/src/app/(app)/clientes/page.tsx` (ejemplo de Server Component)

```typescript
async function getInitialClientes(): Promise<Cliente[]> {
  const baseUrl =
    process.env.SSR_FETCH_URL
    ?? process.env.SPRING_GATEWAY_URL
    ?? "http://localhost:3080";

  for (let i = 0; i < 3; i++) {
    try {
      const res = await fetch(`${baseUrl}/api/clientes`);
      if (res.ok) {
        const data = await res.json();
        if (Array.isArray(data)) return data;
      }
    } catch {
      if (i < 2) await new Promise((r) => setTimeout(r, 1500));
    }
  }
  return [];
}
```

Las páginas de datos usan **Server Components**: el fetch se ejecuta en el servidor durante SSR. Los datos llegan ya renderizados en el HTML. No hay spinner de carga inicial.

El fetch SSR usa `SSR_FETCH_URL=http://haproxy` (en Docker) para que todo el tráfico pase por HAProxy. Incluye 3 reintentos con pausa para cubrir el tiempo de arranque del backend.

Si SSR no pudo obtener datos (back-end no listo), el **Client Component** tiene un `useEffect` de respaldo que hace un fetch silencioso desde el browser automáticamente, sin mostrar loading ni requerir que el usuario refresque:

```typescript
// ClientesPageClient.tsx
useEffect(() => {
    if (initialClientes.length === 0 && !fetchError) {
        clienteService.getAll()
            .then(setClientes)
            .catch(() => setFetchError("No se pudieron cargar los clientes."));
    }
}, []);
```

La interactividad (CRUD, modales, búsqueda) se delega a **Client Components** (`ClientesPageClient.tsx`) que reciben los datos como prop y mantienen toda la lógica interactiva.

### Estructura de rutas UI

```
/                    → redirect a /dashboard
/login               → página de login (estática)
/dashboard           → panel principal
/clientes            → ABM clientes
/productos           → ABM productos
/pedidos             → ABM pedidos
```

Todas las rutas internas (`dashboard`, `clientes`, `productos`, `pedidos`) están dentro del grupo `(app)`, que aplica el layout con `Navbar` + `Sidebar`. `/login` está fuera del grupo y se renderiza sin navegación.

### Servicios HTTP del frontend

**Archivo:** `frontend/src/services/http.ts`

```typescript
async function request<T>(path: string, options?: RequestInit): Promise<T> {
    const response = await fetch(path, {
        headers: { "Content-Type": "application/json", ...options?.headers },
        ...options,
    });
    if (!response.ok) throw new Error(`Error HTTP ${response.status}`);
    if (response.status === 204) return undefined as T;
    return response.json() as Promise<T>;
}
```

Servicios específicos que consumen las APIs:

| Archivo | Base path | Métodos |
|---|---|---|
| `services/clienteService.ts` | `/api/clientes` | getAll, getById, create, update, delete |
| `services/obraService.ts` | `/api/obras` | getAll, getById, getByClienteId, getSinAsignar, asignarCliente |
| `services/productoService.ts` | `/api/productos` | getAll, getById, create, delete |
| `services/pedidoService.ts` | `/api/pedidos` | getAll, getById, create, delete |

### Proxy route para desarrollo local

**Archivo:** `frontend/src/app/api/[...path]/route.ts`

Cuando se ejecuta `npm run dev` (sin HAProxy), las llamadas a `/api/*` son atrapadas por este catch-all route de Next.js y reenviadas al Gateway. Mapea los recursos a prefijos:

```
/api/clientes/*   → {gateway}/clientes/api/clientes/*
/api/obras/*      → {gateway}/clientes/api/obras/*
/api/productos/*  → {gateway}/productos/api/productos/*
/api/pedidos/*    → {gateway}/pedidos/api/pedidos/*
```

En producción con HAProxy, **no se usa**: HAProxy intercepta `/api/*` antes de llegar a Next.js.

---

## 3. Spring Gateway — Enrutamiento y balanceo

**Archivo:** `dan-gateway/src/main/resources/application.properties`

### Rutas declarativas

| ID | Predicates (Path) | RewritePath | URI (Eureka) |
|---|---|---|---|
| `clientes` | `/clientes/**, /api/clientes/**, /api/obras/**` | `^/clientes/?(?<segment>.*)` → `/$\{segment}` | `lb://ms-clientes` |
| `productos` | `/productos/**, /api/productos/**` | `^/productos/?(?<segment>.*)` → `/$\{segment}` | `lb://ms-productos` |
| `pedidos` | `/pedidos/**, /api/pedidos/**` | `^/pedidos/?(?<segment>.*)` → `/$\{segment}` | `lb://MS-PEDIDOS` |

### Cómo funciona el RewritePath

El anclaje `^` al inicio del regex es crítico. Permite que el Gateway acepte requests desde **dos orígenes distintos**:

| Request llega como | ¿Match `^/clientes/`? | Resultado | Origen |
|---|---|---|---|
| `/clientes/api/clientes` | Sí → segment=`api/clientes` | `/api/clientes` | Next.js proxy (local dev) |
| `/api/clientes` | No → sin rewrite | `/api/clientes` | HAProxy directo (producción) |

`lb://` resuelve la dirección real del microservicio consultando a Eureka, permitiendo balanceo entre réplicas.

### Compresión

```properties
spring.cloud.gateway.compression.enabled=true
```

Habilita compresión gzip en las respuestas del Gateway.

---

## 4. Eureka — Service Discovery

**Archivo:** `dan-eureka-srv/src/main/resources/application.properties`

```properties
server.port=${SPRING_EUREKA_PORT}
eureka.instance.hostname=${SPRING_EUREKA_HOST_NAME}
eureka.client.registerWithEureka=false
eureka.client.fetchRegistry=false
```

Cada microservicio se registra con el mismo patrón (ej. `ms-clientes/src/main/resources/application.properties`):

```properties
eureka.client.serviceUrl.defaultZone=${SPRING_EUREKA_DEFAULT_ZONE}
eureka.client.initialInstanceInfoReplicationIntervalSeconds=5
eureka.client.registryFetchIntervalSeconds=5
eureka.instance.leaseRenewalIntervalInSeconds=5
eureka.instance.leaseExpirationDurationInSeconds=5
```

Polling cada 5s para renovar lease y actualizar el registro.

El Gateway usa `@EnableDiscoveryClient` + `@LoadBalanced WebClient.Builder` para consumir los servicios registrados via `lb://`.

---

## 5. Tabla completa de resolución de rutas

| Llamada desde el frontend | ¿HAProxy hacia? | Gateway recibe | ¿Rewrite? | Microservicio recibe |
|---|---|---|---|---|
| `GET /api/clientes` | Gateway | `/api/clientes` | No (no empieza con `/clientes`) | `GET /api/clientes` |
| `GET /api/clientes/5` | Gateway | `/api/clientes/5` | No | `GET /api/clientes/5` |
| `POST /api/clientes` | Gateway | `/api/clientes` | No | `POST /api/clientes` |
| `PUT /api/clientes/5` | Gateway | `/api/clientes/5` | No | `PUT /api/clientes/5` |
| `DELETE /api/clientes/5` | Gateway | `/api/clientes/5` | No | `DELETE /api/clientes/5` |
| `GET /api/obras` | Gateway | `/api/obras` | No | `GET /api/obras` |
| `GET /api/obras/sin-asignar` | Gateway | `/api/obras/sin-asignar` | No | `GET /api/obras/sin-asignar` |
| `PUT /api/obras/3/asignar-cliente/1` | Gateway | `/api/obras/3/asignar-cliente/1` | No | `PUT /api/obras/3/asignar-cliente/1` |
| `GET /api/productos` | Gateway | `/api/productos` | No | `GET /api/productos` |
| `POST /api/pedidos` | Gateway | `/api/pedidos` | No | `POST /api/pedidos` |

---

## 6. Docker Compose

### `docker-compose.yml` — Backend

Servicios: `rabbitmq`, `ms-eureka-svc`, `mysql`, `ms-clientes-svc`, `pg-dan`, `ms-productos-svc`, `mongo-dan`, `ms-pedidos-svc`, `ms-gateway-svc`.

Todos en red `backend-net` (external, creada previamente). Healthchecks encadenados para garantizar orden de arranque.

### `docker-compose.frontend.yml` — Frontend + HAProxy

```yaml
haproxy:
    image: haproxy:3.0-alpine
    ports: ["80:80"]
    volumes: ["./haproxy/haproxy.cfg:/usr/local/etc/haproxy/haproxy.cfg:ro"]
    depends_on: [frontend-ui]
    networks: [backend-net]

frontend-ui:
    build: ./frontend
    environment:
        SPRING_GATEWAY_URL: http://ms-gateway-svc:8080
        SSR_FETCH_URL: http://haproxy
    expose: ["3000"]
    networks: [backend-net]
```

Dos variables de entorno controlan las URLs de conexión:
- `SPRING_GATEWAY_URL=http://ms-gateway-svc:8080` — usada por el proxy route de Next.js (`api/[...path]/route.ts`) en desarrollo local sin HAProxy
- `SSR_FETCH_URL=http://haproxy` — usada por los Server Components para que el fetch SSR también pase por HAProxy

---

## 7. RabbitMQ — Comunicación asíncrona

Cola durable `stock-update-queue` compartida entre `ms-pedidos` (productor) y `ms-productos` (consumidor). Al crear un pedido, `ms-pedidos` envía mensajes `"idProducto;cantidad"` a la cola; `ms-productos` los consume para actualizar stock.

---

## 8. Comandos

```bash
# Prerrequisito (una sola vez)
docker network create backend-net

# Backend completo
docker compose -f docker-compose.yml up --build

# Frontend + HAProxy
docker compose -f docker-compose.frontend.yml up --build

# Frontend en desarrollo local (sin Docker, sin HAProxy)
cd frontend
npm run dev

# Build individual de un microservicio
cd ms-clientes && ./mvnw clean package -DskipTests
```
