# VigaDesk — Frontend

Aplicación frontend del sistema de gestión de pedidos, construida con **Next.js 16** (App Router), **React 19**, **TypeScript** y **Tailwind CSS 4**.

## Características

- **App Router** de Next.js con separación Server/Client Components.
- **BFF (Backend-For-Frontend):** el route handler `app/api/[...path]/route.ts` actúa de proxy hacia el backend, eliminando problemas de CORS y centralizando el acceso a los microservicios.
- **Autenticación JWT** con sesión persistida (contexto React + `localStorage`).
- **Roles:** `ADMIN` y `CLIENTE` (el cliente solo ve sus propios pedidos). `GUEST` queda restringido al catálogo si existe un token legado.
- Módulos: dashboard, clientes, obras, productos, pedidos y usuarios.
- **Seguridad:** headers de seguridad y CSP en `next.config.ts`, sin credenciales en el bundle.

## Requisitos

- Node.js 20+ y npm.
- El backend corriendo (ver `README.md` de la raíz).

## Puesta en marcha

```bash
npm ci
cp .env.example .env.local   # ajustar HAPROXY_URL si es necesario
npm run dev
```

La app queda disponible en `http://localhost:3000` (o en el puerto configurado). En desarrollo, el BFF proxy apunta a `http://haproxy:80`; si el backend no corre en Docker, cambiá `HAPROXY_URL` en `.env.local`.

## Scripts

| Comando | Descripción |
|---|---|
| `npm run dev` | Servidor de desarrollo |
| `npm run build` | Build de producción (con type-check) |
| `npm start` | Servir el build de producción |
| `npm run lint` | ESLint (neostandard) |

## Docker

```bash
docker build -t dan-frontend-ui:latest .
```

La imagen usa `output: 'standalone'`, corre con un **usuario no-root** y expone el puerto `3000`.
