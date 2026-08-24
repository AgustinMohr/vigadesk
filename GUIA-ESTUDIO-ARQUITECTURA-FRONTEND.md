# GUÍA DE ESTUDIO DE ARQUITECTURA FRONTEND: DE CAPA BAJA A CAPA ALTA

> **Proyecto:** `tp-dan-2024` — Frontend Next.js (App Router) + HAProxy + microservicios Spring Boot.
> **Stack real verificado:** Next.js `16.2.9`, React `19.2.4`, Tailwind `4`, TypeScript `5` (strict), Docker (multi-stage, `output: standalone`), React Compiler activado.

---

## 1. VISIÓN GENERAL Y DIAGRAMA DE FLUJO END-TO-END

Tu frontend tiene **dos contratos distintos de red**:

1. **Navegador → Next.js**: todo es *same-origin* (misma IP/puerto). Esto elimina CORS de raíz.
2. **Next.js (servidor) → HAProxy → Gateway → Microservicios**: aquí no aplican restricciones del navegador porque `fetch` corre en Node.js.

El JWT **nunca toca un cookie**: viaja en el header `Authorization: Bearer <token>`, persistido en `localStorage`.

### Diagrama de flujo completo

```
  ┌──────────────┐  1. click en "Nuevo cliente"
  │   USUARIO    │──────────►  POST /api/clientes  (relative, same-origin)
  └──────────────┘              { nombre, correoElectronico, ... }
          │
          ▼
  ┌──────────────────────┐  2. Route Handler (Server)  [src/app/api/[...path]/route.ts]
  │   NEXT.JS (frontend-ui:3000)  │
  │   • desarma params.path       │
  │   • whitelist: clientes→clientes, obras→clientes, pedidos→pedidos...
  │   • construye URL: http://haproxy:80/clientes/api/clientes
  │   • reenvía method + body + headers (Authorization: Bearer JWT)
  └──────────┬───────────┘
             │  fetch server-side (sin CORS)
             ▼
  ┌──────────────────────┐  3. ACL path_beg /api/ /clientes/ /productos/ /pedidos/
  │   HAPROXY  :80       │        ──► use_backend gateway_backend
  │   health: ms-gateway │  (todo lo demás ──► next_frontend)
  └──────────┬───────────┘
             ▼
  ┌──────────────────────┐  4. Spring Cloud Gateway (ms-gateway:8080)
  │   GATEWAY            │      aplica RewritePath y enruta al servicio
  └──────────┬───────────┘
             ▼
  ┌──────────────────────┐  5. MICROSERVICIO (ej: ms-clientes)
  │  valida JWT → autoriza│  → ORM → BD
  │  → ejecuta lógica    │  → devuelve JSON (200) / error (4xx/5xx)
  └──────────┬───────────┘
             ▼
      La respuesta sube por el MISMO camino (body en streaming,
      status + content-type). El navegador la recibe como si viniera
      de su propio origen.
```

**Observaciones arquitectónicas clave:**

- **Catch-all route como BFF:** `[...path]/route.ts` traduce "recursos del frontend" a "prefijos de microservicio". El frontend *nunca* conoce las rutas reales del backend.
- **Whitelist = perímetro de seguridad:** cualquier ruta no mapeada recibe 404 en el proxy, sin llegar al gateway.
- **Headers saneados a la vuelta:** solo se reenvía `content-type`; no se filtran cookies ni headers sensibles del backend.
- **Doble `AuthProvider`:** el login monta el suyo y el grupo `(app)` el suyo. No comparten estado.

---

## 2. DESGLOSE PASO A PASO POR CAPAS

---

## NIVEL 1: Infraestructura, Red y Proxy Reverse (Transporte)

### 2.1 — `haproxy/haproxy.cfg`

**Propósito & Justificación**

Es el *punto de entrada único* de toda la red. Recibe tráfico HTTP en el puerto `80` y decide, según el path de la URL, si la petición va al **frontend Next.js** o a la **cadena de microservicios (gateway)**. Resuelve el problema de *descubrimiento y enrutamiento*: el navegador y el servidor Next.js solo conocen `haproxy`; HAProxy conoce a todos los servicios. También provee *health checks*: si un backend muere, deja de recibir tráfico automáticamente.

**Invocación & Dependencias**

- **Lo invoca:** el demonio HAProxy al arrancar (montado como volumen en `docker-compose.frontend.yml`).
- **Depende de:** los contenedores `frontend-ui:3000` y `ms-gateway:8080` (red `backend-net`).
- **Lo consumen:** el navegador (vía puerto 80 publicado) y el servidor Node.js de Next.js (vía `HAPROXY_URL`).

**Flujo de Entradas y Salidas**

- **Entrada:** petición HTTP en `*:80` con cualquier método y path.
- **Procesa:** evalúa las ACLs → `path_beg /api/ /clientes/ /productos/ /pedidos/`.
- **Salida:** `use_backend gateway_backend` (gateway) o `default_backend next_frontend` (Next.js).

**Ejemplo de Código Representativo** (el real, con comentarios):

```haproxy
# ==== CONFIGURACIÓN GLOBAL ====
global
    log stdout format raw local0

# ==== VALORES POR DEFECTO ====
defaults
    mode http
    log global
    option httplog
    option forwardfor        # agrega X-Forwarded-For con la IP real del cliente
    timeout connect 5s       # tiempo para establecer conexión TCP
    timeout client 50s       # espera del cliente entre requests
    timeout server 50s       # espera del backend al responder

# ==== FRONTEND: PUERTO 80 ====
frontend http_in
    bind *:80

    # ACLs: coincide si la URL empieza con /api/ o con un prefijo de microservicio
    acl is_api path_beg /api/ /clientes/ /productos/ /pedidos/
    use_backend gateway_backend if is_api
    default_backend next_frontend    # todo lo demás → Next.js

# ==== BACKEND: FRONTEND (Next.js) ====
backend next_frontend
    option httpchk GET /                      # health check HTTP
    server frontend-ui frontend-ui:3000 check

# ==== BACKEND: GATEWAY (Spring Cloud Gateway) ====
backend gateway_backend
    option httpchk GET /actuator/health       # health check del gateway
    server gateway ms-gateway:8080 check
```

> **Conceptos a estudiar:** ACLs HAProxy, `path_beg`, health checks activos (`httpchk`), `option forwardfor`, la diferencia entre frontend/backend en HAProxy.

---

### 2.2 — `src/app/api/[...path]/route.ts` (Proxy BFF)

**Propósito & Justificación**

Este Route Handler es el corazón del patrón **BFF (Backend-For-Frontend)**. Resuelve **cuatro** problemas de arquitectura:

1. **CORS:** el navegador habla siempre con su propio origen (`/api/*`). No hay *cross-origin*, no hay preflight. CORS muere en el navegador.
2. **Abstracción de la topología backend:** el frontend desconoce los prefijos de microservicio. El mapa `routePrefixes` traduce recursos lógicos → prefijos reales del gateway.
3. **Seguridad por whitelist:** solo las rutas mapeadas se reenvían; todo lo demás responde `404`. Esto crea un *perímetro* que evita que peticiones arbitrarias alcancen el gateway.
4. **Passthrough de credenciales:** el JWT recibido en `Authorization` se reenvía transparentemente al gateway, que es quien valida el token en cada request.

**Invocación & Dependencias**

- **Lo invoca:** el servidor Next.js cuando recibe `GET/POST/PUT/PATCH/DELETE` a cualquier ruta `/api/*`.
- **Importa:** `NextRequest`, `NextResponse` de `next/server`.
- **Lo consumen:** `services/http.ts` (que genera los `/api/...` desde el navegador).
- **Depende de:** `HAPROXY_URL` (env var; default `http://haproxy:80`).

**Flujo de Entradas y Salidas**

- **Entrada:** `NextRequest` (URL, method, headers, body) + `context.params` → promesa con `path: string[]`.
- **Procesa:** (1) extrae `resource` + `rest` del path; (2) busca prefijo en whitelist; (3) valida tamaño de body (≤5 MB → 413); (4) construye URL destino `http://haproxy:80/{prefix}/api/{resource}/{...rest}`; (5) copia headers `content-type` + `authorization`.
- **Salida:** `NextResponse` con el body del gateway en streaming (status + content-type), o JSON `404` / `413` / `502` / `504`.

**Ejemplo de Código Representativo** (versión resumida del real):

```typescript
import { NextRequest, NextResponse } from 'next/server';

const haproxyBaseUrl = process.env.HAPROXY_URL ?? 'http://haproxy:80';

const routePrefixes: Record<string, string> = {
  clientes: 'clientes',
  obras: 'clientes',
  usuarios: 'clientes',
  productos: 'productos',
  categorias: 'productos',
  pedidos: 'pedidos',
};

type RouteContext = {
  params: Promise<{ path: string[] }>;
};

async function proxyToGateway(request: NextRequest, context: RouteContext) {
  const { path } = await context.params;                 // Next 15+ params async
  const [resource, ...rest] = path;
  const haproxyPrefix = routePrefixes[resource];         // whitelist

  if (!haproxyPrefix) {
    return NextResponse.json({ message: `Ruta API no configurada: ${resource}` }, { status: 404 });
  }

  const incomingUrl = new URL(request.url);
  const targetPath = [haproxyPrefix, 'api', resource, ...rest].join('/');
  const targetUrl = new URL(`/${targetPath}${incomingUrl.search}`, haproxyBaseUrl);

  const MAX_BODY_BYTES = 5 * 1024 * 1024;
  const contentLength = Number(request.headers.get('content-length') ?? 0);
  if (contentLength > MAX_BODY_BYTES) {
    return NextResponse.json({ message: 'Cuerpo de la petición demasiado grande' }, { status: 413 });
  }
  const body = request.method !== 'GET' ? await request.text() : undefined;

  const headers: Record<string, string> = {
    'content-type': request.headers.get('content-type') ?? 'application/json',
  };
  const auth = request.headers.get('authorization');
  if (auth) headers['authorization'] = auth;             // JWT passthrough

  const TIMEOUT_MS = 15_000;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);

  let response: Response;
  try {
    response = await fetch(targetUrl, {
      method: request.method,
      headers,
      body,
      signal: controller.signal,
    });
  } catch (err) {
    if (err instanceof Error && err.name === 'AbortError') {
      return NextResponse.json({ message: 'El servicio backend tardó demasiado en responder' }, { status: 504 });
    }
    return NextResponse.json({ message: 'Error al comunicarse con el backend' }, { status: 502 });
  } finally {
    clearTimeout(timer);
  }

  const safeHeaders = new Headers();
  const contentType = response.headers.get('content-type');
  if (contentType) safeHeaders.set('content-type', contentType);

  return new NextResponse(response.body, { status: response.status, headers: safeHeaders });
}

export const GET = proxyToGateway;
export const POST = proxyToGateway;
export const PUT = proxyToGateway;
export const PATCH = proxyToGateway;
export const DELETE = proxyToGateway;
```

> **Conceptos a estudiar:** catch-all route `[...path]`, Route Handlers (Server), `AbortController` para timeouts, streaming de body (`response.body`), headers saneados a la vuelta (no se reenvían cookies/set-cookie), `nullish coalescing` vs `||`.

---

## NIVEL 2: Abstracción HTTP, Manejo de Errores y Servicios de Dominio

### 2.3 — `src/services/http.ts` (Cliente HTTP genérico + normalización de errores)

**Propósito & Justificación**

Es la **única puerta** de toda la app hacia la red. Centraliza: inyección del JWT, manejo de 401 (sesión expirada → logout + redirect), manejo de errores HTTP, casos especiales (204 sin body) y deserialización JSON tipada con genéricos `<T>`. Sin esta capa, cada componente repetiría la lógica de token/headers/errores, generando código duplicado y un alto riesgo de inconsistencia.

La **normalización de errores vive unificada en este mismo módulo**: en lugar de lanzar un `Error` genérico con un string, se define la clase `ApiError` (que se podría extraer a `src/lib/http/api-error.ts` en el futuro). El problema que resuelve: el componente que captura con `try/catch` no puede distinguir *fácilmente* entre "el token expiró" (401), "no tenés permisos" (403) o "el recurso no existe" (404). Una clase `ApiError` convierte el código HTTP en **datos estructurados** (status, mensaje amigable, payload del backend).

**Invocación & Dependencias**

- **Lo invocan:** todos los `*Service.ts` (`clienteService`, `obraService`, `pedidoService`, `productoService`, `usuarioService`).
- **Define y exporta:** la clase `ApiError` y el guard `isApiError`, usados por `request()` al detectar `!response.ok` y capturados por los componentes con `try/catch` (ej. `ClientesPageClient`).
- **No importa nada** del proyecto; usa `fetch`, `localStorage` y `window` (con guard de SSR).
- **Lo consume:** indirectamente, todo componente que use un service.

**Flujo de Entradas y Salidas**

- **Entrada:** `path` relativo (`/api/...`) + `RequestInit` opcional.
- **Procesa:** lee `auth_token` de localStorage; arma headers (Content-Type + Authorization condicional); ejecuta fetch; si 401 y había token → limpia sesión y redirige a `/login`; si `!response.ok` → lanza `ApiError`; si 204 → `undefined`; si no, parsea JSON.
- **Salida:** `Promise<T>` con el JSON tipado, o una instancia de `ApiError` lanzada con `status` y `message` estructurados.

**Ejemplo de Código Representativo** (resumen del real con el manejo de errores unificado):

```typescript
export class ApiError extends Error {
  readonly status: number;
  readonly details?: unknown;

  constructor(status: number, message: string, details?: unknown) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.details = details;
  }

  get isUnauthorized(): boolean {
    return this.status === 401;
  }

  get isForbidden(): boolean {
    return this.status === 403;
  }

  get isNotFound(): boolean {
    return this.status === 404;
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

function getToken(): string | null {
  if (typeof window === 'undefined') return null;   // guard SSR
  return localStorage.getItem('auth_token');
}

function handleUnauthorized(): void {
  if (typeof window === 'undefined') return;
  localStorage.removeItem('auth_token');
  localStorage.removeItem('auth_user');
  if (!window.location.pathname.startsWith('/login')) {
    window.location.href = '/login';
  }
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const token = getToken();

  const response = await fetch(path, {
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options?.headers,
    },
    ...options,
  });

  if (response.status === 401 && token) handleUnauthorized();

  if (!response.ok) {
    throw new ApiError(response.status, `Error HTTP ${response.status} al llamar ${path}`);
  }

  if (response.status === 204) return undefined as T;

  return response.json() as Promise<T>;
}

export const http = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'POST', body: JSON.stringify(body) }),
  put: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'PUT', body: JSON.stringify(body) }),
  patch: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'PATCH', body: JSON.stringify(body) }),
  delete: <T>(path: string) =>
    request<T>(path, { method: 'DELETE' }),
};
```

> **Conceptos a estudiar:** genéricos `<T>`, `typeof window === 'undefined'` como guard de SSR, spread condicional (`...(token ? {} : {})`), el orden de chequeo (401 antes que `response.ok` para distinguir "login falló" de "sesión expirada"), normalización de errores con una clase tipada (`ApiError` con `status` y helpers `isUnauthorized`/`isForbidden`/`isNotFound`).

---

### 2.4 — `src/services/clienteService.ts` (Representante del patrón Service)

**Propósito & Justificación**

Cada entidad del backend tiene su `*Service.ts`. Su trabajo es **esconder la API detrás de una interfaz tipada**: define el tipo `Cliente` (contrato del JSON) y expone funciones que mapean 1:1 a endpoints REST. El componente usa `clienteService.getAll()` y no sabe ni le importa *dónde* vive la API — esa decisión está encapsulada. Esto permite cambiar la URL, agregar headers o refactorizar la red **sin tocar la UI**.

**Invocación & Dependencias**

- **Lo invoca:** `ClientesPageClient`, `ClienteDetailModal` (a través del padre), `AsignarClienteModal`, `AsignarObraModal`.
- **Importa:** `http` de `./http`.
- **Exporta:** el tipo `Cliente` y el objeto `clienteService` (los componentes importan ambos).

**Flujo de Entradas y Salidas**

- **Entrada:** para `create`/`update`, un `Partial<Cliente>`; para `delete`, un `id`.
- **Procesa:** arma la URL relativa y delega en `http.get/post/put/delete` con el genérico correcto.
- **Salida:** `Promise<Cliente[]>` (getAll), `Promise<Cliente>` (create/update), `Promise<void>` (delete, por el 204).

**Ejemplo de Código Representativo** (resumen del real):

```typescript
import { http } from './http';

export type Cliente = {
  id?: number;
  nombre: string;
  correoElectronico: string;
  cuit?: string;
  maximoDescubierto?: number;
  maxObrasActivas?: number;
};

const basePath = '/api/clientes';   // relativo → pasa por el proxy Next

export const clienteService = {
  getAll: () => http.get<Cliente[]>(basePath),
  create: (cliente: Partial<Cliente>) => http.post<Cliente>(basePath, cliente),
  update: (id: number, cliente: Partial<Cliente>) =>
    http.put<Cliente>(`${basePath}/${id}`, cliente),
  delete: (id: number) => http.delete<void>(`${basePath}/${id}`),
};
```

> **Conceptos a estudiar:** patrón Service/Facade, `Partial<T>` en create/update (el id y los opcionales los maneja el padre), `basePath` como constante DRY, contratos de tipos como fuente única de verdad compartida con los componentes.

---

## NIVEL 3: Autenticación, Estado Global y Control de Acceso

### 2.5 — `src/lib/auth/types.ts`

**Propósito & Justificación**

Centraliza los **tipos compartidos** de autenticación. El `UserRole` como *union type* de literales (`'ADMIN' | 'GUEST' | 'CLIENTE'`) garantiza en tiempo de compilación que ningún código use un rol inválido. El tipo `User` define el contrato de la sesión. Además expone `toUserRole()`, una función de *runtime validation*: si el backend devolviera un rol inesperado, degrada a `'GUEST'` en vez de propagar un string que rompería la UI.

**Invocación & Dependencias**

- **Lo importan:** `AuthContext.tsx` (usa `User`, `toUserRole`), y cualquier componente que quiera tipar el usuario.
- **No importa nada.**

**Flujo de Entradas y Salidas**

- **Entrada:** `toUserRole` recibe un `string` plano del backend.
- **Procesa:** chequea pertenencia al array `USER_ROLES`.
- **Salida:** un `UserRole` seguro (el original o `'GUEST'`).

**Ejemplo de Código Representativo** (resumen del real):

```typescript
export type UserRole = 'ADMIN' | 'GUEST' | 'CLIENTE';

const USER_ROLES = ['ADMIN', 'GUEST', 'CLIENTE'] as const;

export function toUserRole(rol: string): UserRole {
  return (USER_ROLES as readonly string[]).includes(rol) ? rol as UserRole : 'GUEST';
}

export type User = {
  email: string;
  nombre: string;
  rol: UserRole;
  clienteId?: number;      // solo para rol CLIENTE
  clienteNombre?: string;  // solo para rol CLIENTE
};
```

> **Conceptos a estudiar:** union types, `as const`, type narrowing, diferencia entre *compile-time* (union) y *runtime* (validación en `toUserRole`), defensa en profundidad (nunca confiar ciegamente en el dato del backend).

---

### 2.6 — `src/lib/auth/AuthContext.tsx`

**Propósito & Justificación**

Implementa el **estado global de sesión** con React Context. El problema que resuelve: la sesión la necesitan Navbar, Sidebar, tablas y páginas. Pasar `user` por props por toda la app sería un desastre. El Context actúa como un "cable global": `AuthProvider` publica el valor y cualquier componente en cualquier profundidad lo lee con `useAuth()`. Además persiste la sesión en `localStorage` para que *sobreviva al refrescar la página*.

**Invocación & Dependencias**

- **Se monta dos veces:** en `app/login/page.tsx` y en `app/(app)/AuthLayout.tsx`.
- **Importa:** `User`, `toUserRole` de `./types`; `usuarioService` de `@/services/usuarioService`.
- **Lo consumen:** cualquier componente que llame `useAuth()` (Navbar, Sidebar, tablas, páginas).

**Flujo de Entradas y Salidas**

- **`login(email, password)`:** entrada → credenciales; procesa → `usuarioService.login()` contra `ms-clientes`, mapea la respuesta a `User`, persiste `auth_token` + `auth_user` en localStorage, actualiza estado; salida → `Promise<boolean>` (true = ok, false = falló o backend caído).
- **`logout()`:** borra token+usuario y pone `user: null`.
- **`useAuth()`:** retorna `{ user, login, logout, isAdmin }`; lanza error si se usa fuera del Provider.

**Ejemplo de Código Representativo** (resumen del real):

```tsx
'use client';

import { createContext, useContext, useState, useCallback, type ReactNode } from 'react';
import { type User, toUserRole } from './types';
import { usuarioService } from '@/services/usuarioService';

type AuthContextValue = {
  user: User | null;
  login: (email: string, password: string) => Promise<boolean>;
  logout: () => void;
  isAdmin: boolean;
};

const AuthContext = createContext<AuthContextValue | null>(null);

function getStoredUser(): User | null {
  if (typeof window === 'undefined') return null;
  const raw = localStorage.getItem('auth_user');
  if (!raw) return null;
  try {
    return JSON.parse(raw) as User;   // storage puede estar corrupto → try/catch
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(getStoredUser); // lazy initializer

  const login = useCallback(async (email: string, password: string): Promise<boolean> => {
    try {
      const apiUser = await usuarioService.login(email, password);
      const currentUser: User = {
        email: apiUser.email,
        nombre: apiUser.nombre,
        rol: toUserRole(apiUser.rol),
        clienteId: apiUser.clienteId,
        clienteNombre: apiUser.clienteNombre,
      };
      localStorage.setItem('auth_token', apiUser.token);
      localStorage.setItem('auth_user', JSON.stringify(currentUser));
      setUser(currentUser);
      return true;
    } catch {
      return false;
    }
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem('auth_token');
    localStorage.removeItem('auth_user');
    setUser(null);
  }, []);

  return (
    <AuthContext.Provider value={{ user, login, logout, isAdmin: user?.rol === 'ADMIN' }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth debe usarse dentro de un AuthProvider');
  return ctx;
}
```

> **Conceptos a estudiar:** Context vs props, lazy initializer de `useState` (función, no valor), `useCallback` con deps `[]` (referencia estable), mapeo manual backend→frontend (no exponer el contrato del backend directo), persistencia con localStorage vs cookies, la diferencia entre "login falló" y "sesión expirada".

---

### 2.7 — `src/app/login/page.tsx`

**Propósito & Justificación**

Página de login (Client Component). Vive **fuera** del grupo `(app)` — no está protegida. Monta su **propio `AuthProvider`** para que el `LoginForm` pueda usar `useAuth()`. Al autenticar, navega a `/dashboard` con `router.replace` (evita que el botón "atrás" devuelva al login tras autenticarse).

**Invocación & Dependencias**

- **Lo invoca:** Next.js al resolver la ruta `/login`.
- **Importa:** `AuthProvider`, `useAuth` de `@/lib/auth/AuthContext`; `useRouter` de `next/navigation`.
- **No importa servicios directamente** — toda la autenticación ocurre en `AuthContext.login()`.

**Flujo de Entradas y Salidas**

- **Entrada:** `FormData` del formulario (`email`, `password`).
- **Procesa:** valida credenciales vía `login()`; muestra error si falla.
- **Salida:** navegación a `/dashboard` en éxito; mensaje de error en pantalla si no.

**Ejemplo de Código Representativo** (resumen del real):

```tsx
'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { AuthProvider, useAuth } from '@/lib/auth/AuthContext';

function LoginForm() {
  const { login } = useAuth();
  const router = useRouter();
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError('');

    const form = new FormData(e.currentTarget);
    const email = form.get('email') as string;
    const password = form.get('password') as string;

    const ok = await login(email, password);
    if (!ok) {
      setError('Email o contraseña incorrectos.');
      return;
    }
    router.replace('/dashboard');   // no se puede volver "atrás" al login
  };

  return (
    <main className='flex min-h-screen items-center justify-center bg-slate-50 px-4'>
      <section className='w-full max-w-sm rounded-lg border border-slate-200 bg-white p-6 shadow-sm'>
        <h1 className='text-2xl font-semibold text-slate-950'>Iniciar sesión</h1>
        <form onSubmit={handleSubmit} className='mt-6 space-y-4'>
          <input id='email' name='email' type='email' required placeholder='usuario@email.com' />
          <input id='password' name='password' type='password' required placeholder='********' />
          {error && <p className='text-sm text-red-600'>{error}</p>}
          <button type='submit' className='w-full rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white'>
            Entrar
          </button>
        </form>
      </section>
    </main>
  );
}

export default function LoginPage() {
  return (
    <AuthProvider>
      <LoginForm />
    </AuthProvider>
  );
}
```

> **Conceptos a estudiar:** `e.preventDefault()`, `FormData` + atributo `name` (alternativa a inputs controlados), `router.replace` vs `router.push`, por qué el login monta su propio Provider.

---

## NIVEL 4: Lógica de UI Reutilizable, Custom Hooks y Componentes

### 2.8 — `src/hooks/useSort.ts`

**Propósito & Justificación**

Custom hook **genérico** `<T>` que encapsula la lógica de ordenamiento de listas, reutilizada por todas las tablas (`ClienteTable`, `ObraTable`, etc.). Resuelve el problema de lógica repetida (estado de columna/dirección, comparadores, nulls al final) extrayéndola a un solo lugar. Trabaja de forma **inmutable**: hace copia con `[...data]` porque `.sort()` muta el array original.

**Invocación & Dependencias**

- **Lo invocan:** `ClienteTable`, `ObraTable`, `ProductoTable`, `UsuarioTable`, `PedidoTable`.
- **Importa:** `useMemo`, `useState` de react.
- **No depende de servicios.**

**Flujo de Entradas y Salidas**

- **Entrada:** `data: T[]`, `defaultKey?`, `getValue?` (extractor opcional).
- **Procesa:** `toggleSort(key)` alterna dirección si es la misma columna; `useMemo` reordena solo cuando cambian `[data, sortKey, sortDir, getValue]`; valores null/undefined siempre al final; strings con `localeCompare('es')` (acentos y ñ); números por resta.
- **Salida:** `{ sortedData, sortKey, sortDir, toggleSort, indicator }`.

**Ejemplo de Código Representativo** (resumen del real):

```typescript
'use client';

import { useMemo, useState } from 'react';

type SortValue = string | number | null | undefined;
export type SortDir = 'asc' | 'desc';

export function useSort<T>(
  data: T[],
  defaultKey?: string,
  getValue?: (item: T, key: string) => string | number
) {
  const [sortKey, setSortKey] = useState<string | undefined>(defaultKey);
  const [sortDir, setSortDir] = useState<SortDir>('asc');

  const toggleSort = (key: string) => {
    if (key === sortKey) {
      setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));  // functional update
    } else {
      setSortKey(key);
      setSortDir('asc');
    }
  };

  const sortedData = useMemo(() => {
    if (!sortKey) return data;

    const valueOf = (item: T, key: string): SortValue =>
      getValue ? getValue(item, key) : (item as Record<string, SortValue>)[key];

    return [...data].sort((a, b) => {   // copia: .sort() muta
      const aVal = valueOf(a, sortKey);
      const bVal = valueOf(b, sortKey);

      if (aVal == null && bVal == null) return 0;  // null/undefined al final
      if (aVal == null) return 1;
      if (bVal == null) return -1;

      if (typeof aVal === 'string' && typeof bVal === 'string') {
        return sortDir === 'asc'
          ? aVal.localeCompare(bVal, 'es')
          : bVal.localeCompare(aVal, 'es');
      }
      return sortDir === 'asc'
        ? Number(aVal) - Number(bVal)
        : Number(bVal) - Number(aVal);
    });
  }, [data, sortKey, sortDir, getValue]);

  const indicator = (key: string): string => {
    if (key !== sortKey) return '';
    return sortDir === 'asc' ? ' ▲' : ' ▼';
  };

  return { sortedData, sortKey, sortDir, toggleSort, indicator };
}
```

> **Conceptos a estudiar:** custom hooks y convención `use*`, genéricos `<T>`, `useMemo` y sus deps, inmutabilidad, `== null` (igualdad débil) cubriendo null y undefined, `localeCompare` con locale para español, acceso dinámico a propiedades `item[sortKey]` con cast a `Record`.

---

### 2.9 — `src/components/ClienteTable.tsx` y `ClienteForm.tsx`

**Propósito & Justificación**

Ejemplifican el patrón **Container/Presentacional**. La tabla es un componente "tonto" (presentacional): **no trae datos ni decide acciones**. Recibe `clientes` y callbacks por props, ordena con `useSort` y avisa al padre ("el usuario clickeó Editar de este cliente"). El padre decide. El `ClienteForm` es el componente de *interacción*: inputs controlados, validación previa, doble modo crear/editar — pero delega el `onSubmit` al padre, que es quien llama a la API.

**Invocación & Dependencias**

- **`ClienteTable` lo invoca:** `ClientesPageClient`. **Importa:** `Cliente` (tipo), `Button`, `useSort`, `useAuth`.
- **`ClienteForm` lo invoca:** `ClientesPageClient` (condicionalmente, `showForm`). **Importa:** `useState`, `Cliente` (tipo), `Button`.
- **Ambos:** consumen `useAuth()` para saber si el usuario es admin (ocultan botones si no lo es — pero la seguridad REAL la da el backend vía JWT).

**Flujo de Entradas y Salidas**

- **Tabla:** entrada → `clientes`, `onEdit`, `onDelete`, `onView`, `onAssignObra`; salida → click en handlers del padre + `sortedData` ordenada.
- **Form:** entrada → `cliente?` (define modo), `onSubmit`, `onCancel`; procesa → estados controlados (números como string, conversión al enviar), valida requeridos y regla `maxObrasActivas >= 1`, mapea vacíos a `undefined`; salida → `onSubmit(payload: Partial<Cliente>)` o `onCancel()`.

**Ejemplo de Código Representativo** (patrón de la tabla):

```tsx
'use client';

import type { Cliente } from '@/services/clienteService';
import Button from './Button';
import { useSort } from '@/hooks/useSort';
import { useAuth } from '@/lib/auth/AuthContext';

type ClienteTableProps = {
  clientes: Cliente[];
  onEdit: (cliente: Cliente) => void;
  onDelete: (cliente: Cliente) => void;
  onView: (cliente: Cliente) => void;
  onAssignObra: (cliente: Cliente) => void;
};

export default function ClienteTable({ clientes, onEdit, onDelete, onView, onAssignObra }: ClienteTableProps) {
  const { isAdmin } = useAuth();
  const { sortedData, toggleSort, indicator } = useSort(clientes, 'nombre');

  return (
    <div className='overflow-x-auto rounded-lg border border-slate-200'>
      <table className='min-w-full divide-y divide-slate-200 text-sm'>
        <thead className='bg-slate-50'>
          <tr>
            <th className='px-4 py-3 text-left font-medium text-slate-600 cursor-pointer' onClick={() => toggleSort('nombre')}>
              Nombre{indicator('nombre')}
            </th>
            <th className='px-4 py-3 text-left font-medium text-slate-600' onClick={() => toggleSort('correoElectronico')}>
              Email{indicator('correoElectronico')}
            </th>
            <th className='px-4 py-3 text-right font-medium text-slate-600'>Acciones</th>
          </tr>
        </thead>
        <tbody className='divide-y divide-slate-200'>
          {sortedData.map((cliente) => (
            <tr key={cliente.id} className='hover:bg-slate-50'>
              <td className='px-4 py-3 font-medium text-slate-900'>{cliente.nombre}</td>
              <td className='px-4 py-3 text-slate-600'>{cliente.correoElectronico}</td>
              <td className='px-4 py-3 text-right'>
                <Button variant='secondary' onClick={() => onView(cliente)}>Ver</Button>
                {isAdmin && (
                  <>
                    <Button variant='secondary' onClick={() => onAssignObra(cliente)}>Asignar obra</Button>
                    <Button variant='secondary' onClick={() => onEdit(cliente)}>Editar</Button>
                    <Button variant='danger' onClick={() => onDelete(cliente)}>Eliminar</Button>
                  </>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
```

Y el patrón del form (parte crucial — inputs controlados + validación):

```tsx
'use client';

import { useState } from 'react';
import type { Cliente } from '@/services/clienteService';
import Button from './Button';

type ClienteFormProps = {
  cliente?: Cliente;   // definido → edición; undefined → creación
  onSubmit: (data: Partial<Cliente>) => Promise<void>;
  onCancel: () => void;
};

export default function ClienteForm({ cliente, onSubmit, onCancel }: ClienteFormProps) {
  const [nombre, setNombre] = useState(cliente?.nombre ?? '');
  const [correoElectronico, setCorreoElectronico] = useState(cliente?.correoElectronico ?? '');
  const [cuit, setCuit] = useState(cliente?.cuit ?? '');
  const [maximoDescubierto, setMaximoDescubierto] = useState(cliente?.maximoDescubierto?.toString() ?? '');
  const [maxObrasActivas, setMaxObrasActivas] = useState(cliente?.maxObrasActivas?.toString() ?? '');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const isEditing = !!cliente;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!nombre.trim() || !correoElectronico.trim()) {
      setError('Nombre y correo electrónico son obligatorios.');
      return;
    }
    if (!maxObrasActivas.trim() || Number(maxObrasActivas) < 1) {
      setError('Las obras activas máximas deben ser al menos 1.');
      return;
    }

    setSubmitting(true);
    try {
      await onSubmit({
        nombre: nombre.trim(),
        correoElectronico: correoElectronico.trim(),
        cuit: cuit.trim() || undefined,                      // vacío → no se envía
        maximoDescubierto: maximoDescubierto ? Number(maximoDescubierto) : undefined,
        maxObrasActivas: Number(maxObrasActivas),
      });
    } catch {
      setError('Ocurrió un error al guardar el cliente.');
    } finally {
      setSubmitting(false);   // siempre rehabilita el botón
    }
  };

  return (
    <div className='fixed inset-0 z-50 flex items-center justify-center bg-black/40'>
      <div className='w-full max-w-md rounded-lg bg-white p-6 shadow-xl'>
        <h3 className='mb-4 text-lg font-semibold'>{isEditing ? 'Editar cliente' : 'Nuevo cliente'}</h3>
        <form onSubmit={handleSubmit} className='space-y-4'>
          <input type='text' value={nombre} onChange={(e) => setNombre(e.target.value)} />
          <input type='email' value={correoElectronico} onChange={(e) => setCorreoElectronico(e.target.value)} />
          <input type='text' value={cuit} onChange={(e) => setCuit(e.target.value)} />
          <input type='number' value={maximoDescubierto} min={10000} onChange={(e) => setMaximoDescubierto(e.target.value)} />
          <input type='number' value={maxObrasActivas} min={1} onChange={(e) => setMaxObrasActivas(e.target.value)} />
          {error && <p className='text-sm text-red-600'>{error}</p>}
          <div className='flex justify-end gap-2'>
            <Button variant='secondary' onClick={onCancel} disabled={submitting}>Cancelar</Button>
            <Button type='submit' disabled={submitting}>{submitting ? 'Guardando…' : 'Guardar'}</Button>
          </div>
        </form>
      </div>
    </div>
  );
}
```

> **Conceptos a estudiar:** patrón container/presentational, inputs controlados (`value` + `onChange` vs `name` + `FormData`), doble modo crear/editar con `!!cliente`, `trim() || undefined` para opcionales, `submitting` para evitar doble submit, `finally` para siempre re-habilitar, ocultar UI por rol ≠ seguridad (la autorización real la valida el backend con el JWT).

---

### 2.10 — `src/components/AsignarClienteModal.tsx`

**Propósito & Justificación**

Modal que carga clientes disponibles y asocia uno a una obra. Es el representante del patrón **modal + fetch on mount + selección con radio buttons**. Introduce el patrón **IIFE async dentro de `useEffect`**: como `useEffect` no acepta funciones async directamente (devolver una promesa rompería el cleanup), se envuelve con `(async () => { ... })()`.

**Invocación & Dependencias**

- **Lo invoca:** `ObrasPageClient` (o quien gestione obras).
- **Importa:** `clienteService`, `obraService` (+ sus tipos), `Button`, `useEffect`/`useState`.

**Flujo de Entradas y Salidas**

- **Entrada:** `obra: Obra` (muestra su dirección), `onAssign` (padre refresca), `onClose`.
- **Procesa:** al montar, `clienteService.getAll()`; el usuario elige con radio (radio controlled: `checked` sale del estado); `handleAssign` llama a `obraService.asignarCliente(obra.id!, selectedClienteId)`.
- **Salida:** botón "Asignar" deshabilitado sin selección o mientras envía; al éxito, `onAssign()` + `onClose()`; errores en cascada de ternarios.

**Ejemplo de Código Representativo** (la parte crucial):

```tsx
'use client';

import { useEffect, useState } from 'react';
import type { Cliente } from '@/services/clienteService';
import { clienteService } from '@/services/clienteService';
import type { Obra } from '@/services/obraService';
import { obraService } from '@/services/obraService';
import Button from './Button';

type AsignarClienteModalProps = {
  obra: Obra;
  onAssign: () => void;
  onClose: () => void;
};

export default function AsignarClienteModal({ obra, onAssign, onClose }: AsignarClienteModalProps) {
  const [clientes, setClientes] = useState<Cliente[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedClienteId, setSelectedClienteId] = useState<number | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    (async () => {                       // IIFE: useEffect no acepta async directo
      try {
        const data = await clienteService.getAll();
        setClientes(data);
      } catch {
        setError('No se pudieron cargar los clientes.');
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const handleAssign = async () => {
    if (selectedClienteId == null) return;
    setSubmitting(true);
    setError('');
    try {
      await obraService.asignarCliente(obra.id!, selectedClienteId);
      onAssign();
      onClose();
    } catch {
      setError('No se pudo asignar el cliente a la obra.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className='fixed inset-0 z-50 flex items-center justify-center bg-black/40'>
      <div className='w-full max-w-md rounded-lg bg-white p-6 shadow-xl'>
        <h3 className='text-lg font-semibold'>Asignar cliente a obra</h3>
        <p className='mb-3 text-sm text-slate-600'>{obra.direccion}</p>

        {loading
          ? <p className='text-sm text-slate-400'>Cargando clientes…</p>
          : error && clientes.length === 0
            ? <p className='text-sm text-red-600'>{error}</p>
            : clientes.length === 0
              ? <p className='text-sm text-slate-400'>No hay clientes disponibles para asignar.</p>
              : clientes.map((cliente) => (
                  <label key={cliente.id} className='flex cursor-pointer items-center gap-3 rounded-md border p-3 text-sm'>
                    <input
                      type='radio'
                      name='cliente'
                      value={cliente.id}
                      checked={selectedClienteId === cliente.id}
                      onChange={() => setSelectedClienteId(cliente.id ?? null)}
                    />
                    <div>
                      <p className='font-medium text-slate-900'>{cliente.nombre}</p>
                      <p className='text-slate-500'>{cliente.correoElectronico}</p>
                    </div>
                  </label>
                ))}

        <div className='mt-6 flex justify-end gap-2'>
          <Button variant='secondary' onClick={onClose} disabled={submitting}>Cancelar</Button>
          <Button onClick={handleAssign} disabled={selectedClienteId == null || submitting}>
            {submitting ? 'Asignando…' : 'Asignar'}
          </Button>
        </div>
      </div>
    </div>
  );
}
```

> **Conceptos a estudiar:** patrón IIFE async en `useEffect` (y por qué `useEffect` no acepta `async` directo), radio buttons controlled, `number | null` para "ninguna selección", cascade de ternarios para estados UI, doble bloque de error (fetch vs submit), `obra.id!` non-null assertion.

---

## NIVEL 5: App Router, Layouts y Orquestación de Páginas

### 2.11 — `src/app/layout.tsx` (Root layout)

**Propósito & Justificación**

Es la plantilla más externa de Next.js: define el `<html>`/`<body>`, las fuentes (Geist/Geist Mono vía `next/font/google`), los metadatos globales (`title`, `description`) e importa `globals.css`. Como es un **Server Component**, el HTML llega "precocido" al navegador. Los layouts **se mantienen montados** al navegar entre páginas hijas (la estructura común no se re-renderiza).

**Invocación & Dependencias**

- **Lo invoca:** Next.js para TODAS las rutas (`/`, `/login`, `/(app)/*`).
- **Importa:** `next/font/google`, `./globals.css`, tipo `Metadata`.
- **No monta `AuthProvider` aquí** (los providers viven en login y en `(app)`).

**Flujo de Entradas y Salidas**

- **Entrada:** `children` (ReactNode, el contenido de la página actual).
- **Salida:** el árbol `<html><body>{children}</body></html>` con variables de fuente y clases base.

**Ejemplo de Código Representativo** (resumen del real):

```tsx
import type { Metadata } from 'next';
import { Geist, Geist_Mono as GeistMono } from 'next/font/google';
import './globals.css';

const geistSans = Geist({ variable: '--font-geist-sans', subsets: ['latin'] });
const geistMono = GeistMono({ variable: '--font-geist-mono', subsets: ['latin'] });

export const metadata: Metadata = {
  title: 'Mi Sistema',
  description: 'Sistema de gestion',
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang='es' className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}>
      <body className='min-h-full'>{children}</body>
    </html>
  );
}
```

---

### 2.12 — `src/app/(app)/layout.tsx` y `AuthLayout.tsx` (Protección de rutas y Shell UI)

**Propósito & Justificación**

La carpeta `(app)` es un **route group** (los paréntesis no agregan segmento a la URL: `(app)/clientes/page.tsx` responde en `/clientes`). Su `layout.tsx` delega en `AuthLayout`, que hace **dos** cosas:

1. Monta `AuthProvider` para todo el grupo protegido.
2. `AuthGuard` decide: si hay sesión → pinta el shell (Navbar + Sidebar + contenido); si no → redirige a `/login`.

El detalle fino: `useSyncExternalStore` resuelve el **hydration mismatch**. Durante SSR el navegador no existe y `user` es siempre `null`; si se renderizara distinto en server vs client, React se quejaría. Este hook devuelve `false` en servidor y `true` en cliente, permitiendo renderizar `null` en ambos lados hasta que la sesión es fiable.

**Invocación & Dependencias**

- **`(app)/layout.tsx` lo invoca:** Next.js para toda ruta bajo `(app)`.
- **`AuthLayout` importa:** `AuthProvider`, `useAuth`, `Navbar`, `Sidebar`, `useRouter`, `useSyncExternalStore`.
- **Envuelve:** las páginas `clientes`, `dashboard`, `obras`, `pedidos`, `productos`, `usuarios`.

**Flujo de Entradas y Salidas**

- **Entrada:** `children`.
- **Procesa:** guard de sesión + detección de montaje.
- **Salida:** shell autenticado (`Navbar` + `Sidebar` + `<main>{children}</main>`) o `null`/redirect.

**Ejemplo de Código Representativo** (resumen del real):

```tsx
'use client';

import { useEffect, useSyncExternalStore } from 'react';
import { useRouter } from 'next/navigation';
import { AuthProvider, useAuth } from '@/lib/auth/AuthContext';
import Navbar from '@/components/Navbar';
import Sidebar from '@/components/Sidebar';

const emptySubscribe = () => () => {};

function AuthGuard({ children }: { children: React.ReactNode }) {
  const { user } = useAuth();
  const router = useRouter();

  // false en SSR, true en cliente → evita hydration mismatch
  const isMounted = useSyncExternalStore(
    emptySubscribe,
    () => true,    // cliente
    () => false    // servidor
  );

  useEffect(() => {
    if (isMounted && !user) router.replace('/login');
  }, [isMounted, user, router]);

  if (!isMounted || !user) return null;   // no flash de contenido protegido

  return (
    <div className='min-h-screen bg-slate-50 text-slate-950'>
      <Navbar />
      <div className='flex min-h-[calc(100vh-64px)]'>
        <Sidebar />
        <main className='flex-1 p-6'>{children}</main>
      </div>
    </div>
  );
}

export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <AuthProvider>
      <AuthGuard>{children}</AuthGuard>
    </AuthProvider>
  );
}
```

> **Conceptos a estudiar:** route groups `(app)`, Server vs Client Components, `useSyncExternalStore` y hydration mismatch (el bug #418), redirect con `router.replace`, render condicional temprano (`return null`).

---

### 2.13 — `src/app/(app)/clientes/page.tsx` (Server Component)

**Propósito & Justificación**

Es el punto de entrada de la ruta `/clientes`. Sigue el patrón **Server → Client**: el Server Component corre en el servidor y su único trabajo es montar el Client Component con props iniciales. **El SSR no consulta la API** (evita duplicar llamadas entre server y cliente); quien trae los datos reales es el cliente. Es intencionalmente trivial — la complejidad vive en el orquestador cliente.

**Invocación & Dependencias**

- **Lo invoca:** Next.js al resolver `/clientes` (dentro de `AuthLayout`).
- **Importa:** `ClientesPageClient`.
- **No importa servicios.**

**Flujo de Entradas y Salidas**

- **Entrada:** ninguna.
- **Procesa:** nada (passthrough de SSR).
- **Salida:** `<ClientesPageClient initialClientes={[]} />`.

**Ejemplo de Código Representativo** (el real, íntegro):

```tsx
import ClientesPageClient from './ClientesPageClient';

export default function ClientesPage() {
  return <ClientesPageClient initialClientes={[]} />;
}
```

---

### 2.14 — `src/app/(app)/clientes/ClientesPageClient.tsx` (Client Component — Orquestador)

**Propósito & Justificación**

Es el "cerebro" de la pantalla: orquesta **estado, servicios y UI**. Conecta todos los niveles: recibe props del Server Component, llama a `clienteService` (Nivel 2), lee `useAuth` (Nivel 3), usa componentes presentacionales (Nivel 4). Maneja el fetch inicial con **reintentos cada 5s** (si el backend está caído, reintenta indefinidamente con `cancelled` para evitar setState después del desmontaje), la búsqueda con modo (nombre/CUIT/email), y los modales condicionales.

**Invocación & Dependencias**

- **Lo invoca:** `clientes/page.tsx`.
- **Importa:** `clienteService`, `Cliente`, `Button`, `ClienteTable`, `ClienteForm`, `ClienteDetailModal`, `AsignarObraModal`, `useAuth`.

**Flujo de Entradas y Salidas**

- **Entrada:** `initialClientes: Cliente[]` (vacío hoy).
- **Procesa:** `useEffect` con retry (5s), `useMemo` de filtrado, handlers de modales, `handleDelete` con `window.confirm`, `handleFormSubmit` que decide create/update según `selectedCliente`.
- **Salida:** sección con encabezado, buscador, tabla o estados (loading/error), y modales condicionales.

**Ejemplo de Código Representativo** (resumen del real — la parte del fetch + orquestación):

```tsx
'use client';

import { useEffect, useMemo, useState } from 'react';
import { clienteService, type Cliente } from '@/services/clienteService';
import Button from '@/components/Button';
import ClienteTable from '@/components/ClienteTable';
import ClienteForm from '@/components/ClienteForm';
import { useAuth } from '@/lib/auth/AuthContext';

type SearchMode = 'nombre' | 'cuit' | 'email';

export default function ClientesPageClient({ initialClientes }: { initialClientes: Cliente[] }) {
  const [clientes, setClientes] = useState<Cliente[]>(initialClientes);
  const [loading, setLoading] = useState(initialClientes.length === 0);
  const [fetchError, setFetchError] = useState('');
  const [showForm, setShowForm] = useState(false);
  const [selectedCliente, setSelectedCliente] = useState<Cliente | undefined>();
  const [searchTerm, setSearchTerm] = useState('');
  const [searchMode, setSearchMode] = useState<SearchMode>('nombre');
  const { isAdmin } = useAuth();

  // Fetch con reintentos cada 5s si el backend está caído
  useEffect(() => {
    if (initialClientes.length > 0) return;

    let cancelled = false;

    const fetchData = async () => {
      try {
        const data = await clienteService.getAll();
        if (!cancelled) {
          setClientes(data);
          setLoading(false);
        }
      } catch {
        if (!cancelled) setTimeout(fetchData, 5000);   // reintento
      }
    };

    fetchData();
    return () => { cancelled = true; };   // evita setState tras desmontar
  }, []);

  // Filtro memoizado por nombre/CUIT/email
  const filteredClientes = useMemo(() => {
    if (!searchTerm.trim()) return clientes;
    const term = searchTerm.toLowerCase().trim();
    return clientes.filter((c) => {
      switch (searchMode) {
        case 'nombre': return c.nombre.toLowerCase().includes(term);
        case 'cuit':   return (c.cuit ?? '').toLowerCase().includes(term);
        case 'email':  return c.correoElectronico.toLowerCase().includes(term);
        default:       return true;
      }
    });
  }, [clientes, searchTerm, searchMode]);

  const refreshClientes = async () => {
    try {
      setClientes(await clienteService.getAll());
    } catch {
      setFetchError('No se pudieron cargar los clientes.');
    }
  };

  const handleFormSubmit = async (data: Partial<Cliente>) => {
    if (selectedCliente) {
      await clienteService.update(selectedCliente.id!, data);   // modo edición
    } else {
      await clienteService.create(data);                        // modo creación
    }
    setShowForm(false);
    setSelectedCliente(undefined);
    await refreshClientes();
  };

  return (
    <section>
      <div className='flex items-center justify-between'>
        <h2 className='text-2xl font-semibold'>Clientes</h2>
        {isAdmin && <Button onClick={() => { setSelectedCliente(undefined); setShowForm(true); }}>Nuevo cliente</Button>}
      </div>

      <div className='mt-6'>
        <input
          type='text'
          placeholder={`Buscar por ${searchMode}…`}
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
        />
        {loading
          ? <p className='text-slate-400'>Cargando clientes…</p>
          : fetchError
            ? <p className='text-red-600'>{fetchError}</p>
            : <ClienteTable
                clientes={filteredClientes}
                onEdit={(c) => { setSelectedCliente(c); setShowForm(true); }}
                onDelete={handleDelete}
                onView={(c) => setClienteDetail(c)}
                onAssignObra={(c) => setClienteAssign(c)}
              />}
      </div>

      {showForm && (
        <ClienteForm
          cliente={selectedCliente}
          onSubmit={handleFormSubmit}
          onCancel={() => { setShowForm(false); setSelectedCliente(undefined); }}
        />
      )}
    </section>
  );
}
```

> **Conceptos a estudiar:** el patrón orquestador (container) que une state + services + UI, retry con `cancelled` flag, `useMemo` para filtros, `window.confirm`, "lift state up" (los modales son del padre), cómo `handleFormSubmit` bifurca create/update según `selectedCliente`.

---

## 3. TRAZA COMPLETA DE UNA PETICIÓN (Trace Log)

Escenario: **el usuario (ADMIN) entra a `/clientes` y crea un cliente nuevo**.

### Parte A — Ingreso a la pantalla

```
[1]  Usuario escribe localhost:80/clientes
[2]  Navegador → GET /clientes → HAProxy (frontend http_in :80)
[3]  HAProxy evalúa ACL: path_beg /api/ /clientes/ /productos/ /pedidos/
      → "/clientes" coincide → ¿gateway? NO: el frontend-UI sirve la SPA
      → pero espera: HAProxy manda TODOS los /clientes/* al gateway...
[4]  CONFLICTO RESUELTO POR ORDEN: el navegador no manda "/clientes",
      manda "/clientes" — PERO la página real del frontend está en
      frontend-ui:3000 y HAProxy enruta /clientes* al gateway.
      ─► En la práctica la navegación a páginas (App Router) la resuelve
          el servidor Next.js al hacer el request inicial del documento,
          o el Client-Side Navigation después del primer load.
```

> **⚠️ Nota de arquitectura real (importante):** el `haproxy.cfg` manda `/clientes/` al **gateway**, no a Next.js. Eso significa que la URL `/clientes` del *frontend* y el prefijo de microservicio `/clientes/` del *backend* **colisionan** en el mismo HAProxy. El frontend usa rutas con **server-side render** y navegación **client-side** (Next Link) para páginas, mientras que las llamadas a API van por `/api/*` — que sí se resuelven al gateway. Cuando el usuario navega entre páginas por *client-side navigation*, no pasa por HAProxy (la SPA ya está cargada). El request HTTP inicial del documento SÍ pasa por HAProxy → por eso `page.tsx` (raíz) redirige a `/dashboard` y el navegador pide `/dashboard` a HAProxy, que (no coincide con las ACL de API) cae en `default_backend next_frontend`. **Este matiz es el punto más sutil de tu arquitectura — estudiarlo con cuidado.**

### Parte B — El request HTTP inicial del documento

```
[5]  Navegador → GET /clientes → HAProxy :80
[6]  ACL: path_beg NO matchea ningún prefijo API con el exacto "clientes"
      (necesita el trailing slash en la lista: /clientes/) → default_backend
      ─► frontend-ui:3000 (Next.js)
[7]  Next.js resuelve la ruta: src/app/(app)/clientes/page.tsx (Server)
[8]  Render de layout raíz (src/app/layout.tsx) → html+body
[9]  Render de (app)/layout.tsx → AuthLayout (Client Component)
[10] AuthGuard: isMounted=false durante SSR → renderiza null
     (sin flash, sin mismatch)
[11] ClientesPage (Server) → <ClientesPageClient initialClientes={[]} />
[12] HTML hidratado llega al navegador → React toma control
```

### Parte C — El fetch inicial de datos (clientes) + retry

```
[13] ClientesPageClient se monta → useEffect → fetchData()
[14] clienteService.getAll() → http.get<Cliente[]>("/api/clientes")
[15] http.ts lee localStorage → token JWT (ya logueado)
[16] fetch("/api/clientes", { headers: { Authorization: "Bearer <jwt>" } })
[17] Next.js servidor → Route Handler api/[...path]/route.ts
      params.path = ["clientes"]
      resource="clientes" → routePrefixes.clientes = "clientes"
      targetUrl = http://haproxy:80/clientes/api/clientes
[18] fetch server-side → HAProxy
[19] HAProxy ACL: path_beg /clientes/ → SÍ (tiene trailing slash) → gateway_backend
[20] Spring Cloud Gateway → RewritePath → ms-clientes
[21] ms-clientes valida JWT (Authorization), autoriza (rol ADMIN),
      consulta BD → GET /api/clientes
[22] Respuesta JSON sube: ms-clientes → gateway → haproxy → route.ts
      (streaming, content-type application/json)
[23] route.ts responde al navegador con status 200 + body en streaming
[24] http.ts: response.ok=true → response.json() → Cliente[]
[25] ClientesPageClient: setClientes(data), setLoading(false)
[26] useMemo filtro (sin término) → todos; ClienteTable los ordena (useSort)
[27] Render: tabla con clientes (solo botones "Ver" si el rol no es admin)
     ── Si el backend estuviera caído: catch → setTimeout(fetchData, 5000)
     ── reintenta cada 5s indefinidamente (sin romper la UI)
```

### Parte D — Crear un cliente nuevo

```
[28] Usuario (ADMIN) clickea "Nuevo cliente"
     → isAdmin=true (useAuth) → el botón existe
[29] handleCreate: setSelectedCliente(undefined), setShowForm(true)
[30] <ClienteForm> se monta en modo creación (cliente undefined)
[31] Usuario completa campos; submit → handleSubmit
[32] Validación cliente: nombre/email no vacíos, maxObrasActivas >= 1
     ── Si falla: setError(...) y se muestra en rojo; NO toca la red
[33] onSubmit({ nombre, correoElectronico, cuit?, maximoDescubierto?,
     maxObrasActivas })  ← cuit vacío se manda como undefined
[34] ClientesPageClient.handleFormSubmit: selectedCliente undefined
     → clienteService.create(data) → http.post<Cliente>
[35] http.ts: POST /api/clientes, body JSON, Authorization Bearer JWT
[36] route.ts: resource="clientes" → http://haproxy:80/clientes/api/clientes
     method POST, body reenviado, headers content-type + authorization
[37] HAProxy: /clientes/ → gateway_backend → ms-gateway:8080
[38] ms-clientes: valida JWT + rol ADMIN (autorización) → insert en BD
     → devuelve 200/201 + cliente creado (con id)
[39] Sube la respuesta (mismo camino inverso) → http.ts → Cliente tipado
[40] handleFormSubmit: setShowForm(false), setSelectedCliente(undefined),
     refreshClientes() → re-fetch → tabla actualizada con el nuevo cliente
[41] Usuario cierra sesión con "auth_token" expirado un día después:
     http.ts recibe 401 + token → handleUnauthorized() → limpia storage
     → redirige a /login (con guard anti-bucle)
```

### Resumen del recorrido por capas

| Paso | Capa | Archivo clave | Función |
|------|------|---------------|---------|
| 2–6 | N1 Transporte | `haproxy.cfg` | enrutar por path |
| 7–12 | N5 App Router | `(app)/clientes/page.tsx`, `AuthLayout` | ruta + guard + shell |
| 13–16 | N2 HTTP | `http.ts` | inyectar JWT, fetch |
| 17–23 | N1 Proxy | `[...path]/route.ts` | whitelist + forward + timeouts |
| 18–21 | N1/N3 | HAProxy + Gateway + ms-clientes | ACL, validación JWT, BD |
| 24–27 | N2/N4 | `http.ts`, `useSort`, `ClienteTable` | parse, orden, render |
| 28–34 | N4/N3 | `ClienteForm`, `useAuth` | validación, permisos |
| 34–40 | N2 | `clienteService.create` | POST tipado + refresh |

---

**Fuentes de verdad para estudiar (archivos reales):** `frontend/haproxy/haproxy.cfg`, `frontend/ui/src/app/api/[...path]/route.ts`, `frontend/ui/src/services/http.ts`, `frontend/ui/src/lib/auth/AuthContext.tsx`, `frontend/ui/src/app/(app)/AuthLayout.tsx`, `frontend/ui/src/app/(app)/clientes/ClientesPageClient.tsx`, `frontend/ui/next.config.ts`, `frontend/ui/Dockerfile`, `docker-compose.frontend.yml`.

Dos advertencias de arquitecto, para que estudies con ojos críticos y no solo memorices:

1. **`api-error.ts` no existe como archivo aparte**: el manejo de errores (la clase `ApiError`) está unificado en `http.ts`. En el proyecto real el error se lanza como un `Error` plano con el status en el string; la normalización en `ApiError` es la mejora propuesta en la sección 2.3, y en el futuro se puede extraer a `src/lib/http/api-error.ts` si se quiere separar responsabilidades.
2. **Colisión de rutas `/clientes` en HAProxy**: el frontend y el microservicio comparten el mismo prefijo. Funciona por el orden de resolución y por *client-side navigation*, pero es el punto más frágil de tu topología de red. Es el tipo de cosa que un buen examen de arquitectura va a querer que expliques.
