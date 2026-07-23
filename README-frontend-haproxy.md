# Frontend con HAProxy y Spring Gateway

Flujo de requests:

```txt
Browser -> HAProxy :80 -> Next.js :3000 -> Spring Gateway :8080 -> microservicios
```

## Rutas de UI

- `/login`
- `/dashboard`
- `/clientes`
- `/productos`
- `/pedidos`

## Rutas de API desde la UI

El navegador llama siempre al servidor de Next:

- `GET /api/clientes`
- `GET /api/productos`
- `GET /api/pedidos`

Next reenvia esas llamadas al gateway usando la variable:

```txt
SPRING_GATEWAY_URL=http://ms-gateway-svc:8080
```

Ejemplo:

```txt
/api/clientes -> http://ms-gateway-svc:8080/clientes/api/clientes
```

## Levantar backend

Primero tiene que existir la red Docker compartida:

```bash
docker network create backend-net
```

Despues levantar Eureka, gateway y microservicios con los compose existentes del proyecto.

## Levantar frontend + HAProxy

Desde la raiz del proyecto:

```bash
docker compose -f docker-compose.frontend.yml up --build
```

Abrir:

```txt
http://localhost
```

HAProxy publica el puerto `80` y envia el trafico al contenedor `frontend-ui`.
