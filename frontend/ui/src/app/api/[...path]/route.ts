// ==============================================================
// PROXY API / BFF (Backend-For-Frontend) — ENRUTADOR DINÁMICO DE MICROSERVICIOS
// --------------------------------------------------------------
// Propósito y Arquitectura:
// Este archivo actúa como una puerta de enlace de red (Reverse Proxy) interna
// dentro del servidor Node.js de Next.js. Se encarga de interceptar todas las
// peticiones HTTP enviadas desde el navegador hacia la ruta `/api/*`.
// El nombre de carpeta `[...path]` es un "catch-all": captura CUALQUIER
// sub-ruta. Ej: /api/obras/5/estado → path = ["obras", "5", "estado"].
//
// Funciones Principales:
// 1. Abstracción e inmunidad a CORS: elimina bloqueos por CORS en el navegador
//    haciendo que las llamadas del cliente sean de un mismo origen (Same-Origin).
// 2. Enrutamiento y traducción de URLs: extrae los segmentos dinámicos
//    capturados por el Catch-all ([...path]), traduce nombres de recursos
//    simplificados (ej: "/api/obras") a rutas internas de backend
//    (ej: "/clientes/api/obras") usando un mapa de prefijos (routePrefixes).
// 3. Filtrado de Seguridad (Whitelist): valida que las rutas solicitadas
//    existan en la configuración; si no están mapeadas, responde 404 JSON.
// 4. Reenvío Transparente (Passthrough): transfiere la petición original con
//    su método (GET/POST/PUT/DELETE/PATCH), encabezados y cuerpo hacia la
//    infraestructura interna (HAProxy / Spring Cloud Gateway).
//
// CONCEPTOS CLAVE: BFF (Backend-For-Frontend), Catch-all route, Same-Origin,
// Whitelist de rutas, Reenvío de headers (JWT passthrough), fetch server-side.
//
// NOTA DE ARQUITECTURA: el frontend solo conoce rutas relativas "/api/*".
// El código de este archivo corre SOLO en el servidor de Node.js (NUNCA en el
// navegador), por lo que aquí SÍ se puede usar `process.env` sin riesgo de
// filtrar secretos al cliente.
// ==============================================================

import { NextRequest, NextResponse } from 'next/server';

// URL base del balanceador HAProxy. Se configura por variable de entorno para
// que funcione distinto según el ambiente:
//  - Dentro de Docker Compose (red interna) → "http://haproxy:80".
//  - En desarrollo local → se puede apuntar a localhost u otra IP.
// El `??` (nullish coalescing) usa el valor por defecto SOLO si la variable es
// `undefined` o `null` (no si es un string vacío).
const haproxyBaseUrl =
  process.env.HAPROXY_URL ?? 'http://haproxy:80';

// Mapeo de recursos a prefijos del gateway.
// El frontend habla de "recursos" (obras, clientes...) pero en el backend cada
// uno vive bajo un prefijo de microservicio. Ej: las obras y usuarios viven
// bajo el microservicio "clientes".
const routePrefixes: Record<string, string> = {
  clientes: 'clientes',
  obras: 'clientes',
  usuarios: 'clientes',
  productos: 'productos',
  categorias: 'productos',
  pedidos: 'pedidos',
};

// Tipo del contexto de ruta que Next.js inyecta en los Route Handlers.
// `params` es una PROMESA que resuelve al array `path` con los
// segmentos capturados por `[...path]`.
type RouteContext = {
  params: Promise<{
    path: string[]; // segmentos capturados por [...path]: "/api/obras/12/detalles -> path=["obras", "12", "detalles"]"
  }>;
};

/**
 * Núcleo del proxy: recibe la petición del navegador, verifica que el recurso
 * exista en la whitelist y la reenvía hacia HAProxy/gateway con el mismo
 * método, body y headers (incluido el JWT en `Authorization`).
 *
 * FLUJO (paso a paso):
 * 1. Desempaqueta los segmentos de la URL capturados por el catch-all.
 * 2. Determina el prefijo de microservicio; si no hay mapeo → 404.
 * 3. Construye la URL de destino y le agrega el query string original.
 * 4. Lee el body solo si el método lo admite (POST/PUT/PATCH).
 * 5. Reenvía headers (content-type + authorization si llegó).
 * 6. Hace fetch server-side al gateway y repite la respuesta al navegador.
 *
 * @param {NextRequest} request - La petición HTTP entrante desde el navegador.
 * @param {RouteContext} context - Contiene la promesa de `params.path` (segmentos).
 * @returns {Promise<NextResponse>} La respuesta del gateway (status, headers y
 *   body en streaming) o un JSON 404 si el recurso no está mapeado.
 */
async function proxyToGateway (request: NextRequest, context: RouteContext) {
  // `await` resuelve la promesa de params (Next.js 15 hace async los params).
  const { path } = await context.params;

  // Desestructuración con resto: `resource` es el primer segmento y `rest`
  // acumula los siguientes. "/api/obras/5" → resource="obras", rest=["5"].
  const [resource, ...rest] = path;
  // Busca el prefijo de microservicio en la whitelist (undefined si no existe).
  const haproxyPrefix = routePrefixes[resource];

  // Recurso no mapeado → 404 (evita que cualquier ruta arbitraria llegue al gateway).
  if (!haproxyPrefix) {
    return NextResponse.json(
      { message: `Ruta API no configurada: ${resource}` },
      { status: 404 }
    );
  }

  // Construcción de la URL absoluta de destino apuntando a HAProxy (puerto 80).
  // La cadena de ruteo es: Next.js → HAProxy → ms-gateway:8080.
  // HAProxy usa la regla 'path_beg' de haproxy.cfg (ej: /clientes/) para
  // redirigir al gateway, y Spring Cloud Gateway aplica 'RewritePath'.
  // Ej: /api/obras/5 -> http://haproxy:80/clientes/api/obras/5
  const incomingUrl = new URL(request.url);          // URL completa de la petición original
  // Reconstruye la ruta interna: prefijo + "api" + recurso + segmentos restantes.
  const targetPath = [haproxyPrefix, 'api', resource, ...rest].join('/');
  // `new URL(ruta, base)` combina ambos: mantiene el query string original.
  const targetUrl = new URL(`/${targetPath}${incomingUrl.search}`, haproxyBaseUrl);

  // Se limita el tamaño del body a 5 MB para evitar abusos de memoria.
  const MAX_BODY_BYTES = 5 * 1024 * 1024;
  const contentLength = Number(request.headers.get('content-length') ?? 0);
  if (contentLength > MAX_BODY_BYTES) {
    return NextResponse.json({ message: 'Cuerpo de la petición demasiado grande' }, { status: 413 });
  }

  // El body solo tiene sentido en métodos que lo envían (POST/PUT/PATCH).
  // `request.text()` lee el cuerpo como string plano para poder re-enviarlo.
  // ArrayBuffer preserva multipart/form-data (CSV) y tambien JSON.
  const body = request.method !== 'GET'
    ? await request.arrayBuffer()
    : undefined;

  // Reenvío de headers al gateway. content-type siempre; el header
  // Authorization (JWT) se copia si el navegador lo mandó, para que el
  // Spring Cloud Gateway pueda validar el token en cada request.
  const headers: Record<string, string> = {
    'content-type': request.headers.get('content-type') ?? 'application/json',
  };
  const auth = request.headers.get('authorization');
  if (auth) {
    headers['authorization'] = auth;
  }

  // Reconstruye la petición que se enviará al gateway con los mismos datos
  // que llegaron del navegador (método, headers, body). `RequestInit` es el
  // tipo estándar de la Fetch API que también acepta NextRequest.
  const init: RequestInit = {
    method: request.method,
    headers,
    body,
  };

  // fetch DEL SERVIDOR hacia el gateway. A diferencia del fetch del navegador,
  // este corre en Node.js y por eso no tiene restricciones de CORS.
  // Se usa un timeout con AbortController: si el gateway no responde en 15s,
  // se aborta y se responde 504 en vez de colgar la petición indefinidamente.
  const TIMEOUT_MS = 15_000;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);

  let response: Response;
  try {
    response = await fetch(targetUrl, { ...init, signal: controller.signal });
  } catch (err) {
    if (err instanceof Error && err.name === 'AbortError') {
      return NextResponse.json({ message: 'El servicio backend tardó demasiado en responder' }, { status: 504 });
    }
    return NextResponse.json({ message: 'Error al comunicarse con el backend' }, { status: 502 });
  } finally {
    clearTimeout(timer);
  }

  // Reenvía al navegador la respuesta con status y body en streaming, pero
  // SOLO con headers controlados (content-type). No se reenvían cookies ni
  // headers sensibles del backend al cliente.
  const safeHeaders = new Headers();
  const contentType = response.headers.get('content-type');
  if (contentType) {
    safeHeaders.set('content-type', contentType);
  }

  return new NextResponse(response.body, {
    status: response.status,
    headers: safeHeaders,
  });
}

// Exportamos el mismo manejador para todos los métodos HTTP. Next expone
// cada export como endpoint: export const GET → maneja peticiones GET, etc.
// Al ser la MISMA función, el resto de segmentos de ruta y query strings se
// reenvían sin distinción de verbo.
export const GET = proxyToGateway;
export const POST = proxyToGateway;
export const PUT = proxyToGateway;
export const PATCH = proxyToGateway;
export const DELETE = proxyToGateway;
