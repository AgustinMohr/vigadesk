/**
 * ============================================================================
 * PROXY API / BFF (Backend-For-Frontend) - ENRUTADOR DINÁMICO DE MICROSERVICIOS
 * ============================================================================
 *
 * Propósito y Arquitectura:
 * Este archivo actúa como una puerta de enlace de red (Reverse Proxy) interna
 * dentro del servidor Node.js de Next.js. Se encarga de interceptar todas las
 * peticiones HTTP enviadas desde el navegador hacia la ruta `/api/*`.
 * El nombre de carpeta `[...path]` es un "catch-all": captura CUALQUIER
 * sub-ruta. Ej: /api/obras/5/estado → path = ["obras", "5", "estado"].
 *
 * Funciones Principales:
 * 1. Abstracción e Inmunidad a CORS: Elimina bloqueos por CORS en el navegador
 *    haciendo que las llamadas del cliente sean de un mismo origen (Same-Origin).
 * 2. Enrutamiento y Traducción de URLs: Extrae los segmentos dinámicos capturados
 *    por el Catch-all ([...path]), traduce nombres de recursos simplificados
 *    (ej: "/api/obras") a rutas internas de backend (ej: "/clientes/api/obras")
 *    utilizando un mapa de prefijos (`routePrefixes`).
 * 3. Filtrado de Seguridad (Whitelist): Valida que las rutas solicitadas existan
 *    en la configuración. Si no se encuentran mapeadas, detiene la ejecución
 *    devolviendo una respuesta 404 en formato JSON.
 * 4. Reenvío Transparente (Passthrough): Transfiere la petición original junto con
 *    sus métodos (GET, POST, PUT, DELETE, PATCH), encabezados y cuerpo (body)
 *    hacia la infraestructura interna (HAProxy / Spring Cloud Gateway).
 *
 * Operación HTTP:
 * Exporta universalmente los métodos HTTP soportados hacia la función central
 * `proxyToGateway`.
 * ============================================================================
 */

import { NextRequest, NextResponse } from 'next/server';

const haproxyBaseUrl =
  process.env.HAPROXY_URL ?? 'http://haproxy:80';

// Mapeo de recursos a prefijos del gateway.
// El frontend habla de "recursos" (obras, clientes...) pero en el
// backend cada uno vive bajo un prefijo de microservicio. Ej: las obras
// y usuarios viven bajo el microservicio "clientes".
const routePrefixes: Record<string, string> = {
  clientes: 'clientes',
  obras: 'clientes',
  usuarios: 'clientes',
  productos: 'productos',
  categorias: 'productos',
  pedidos: 'pedidos',
};

type RouteContext = {
  params: Promise<{
    path: string[]; // segmentos capturados por [...path]: "/api/obras/12/detalles -> path=["obras", "12", "detalles"]"
  }>;
};

// Se encarga de tomar la petición recibida desde el navegador, analizar a qué recurso pertenece
// (por ejemplo, obras, clientes, productos) y verificar si existe un microservicio destino configurado antes de reenviarla.
async function proxyToGateway (request: NextRequest, context: RouteContext) {
  const { path } = await context.params;
  const [resource, ...rest] = path; // "obras", y el resto de segmentos
  const haproxyPrefix = routePrefixes[resource];

  // Recurso no mapeado → 404
  if (!haproxyPrefix) {
    return NextResponse.json(
      { message: `Ruta API no configurada: ${resource}` },
      { status: 404 }
    );
  }

  // Reconstruye la URL de destino apuntando al contenedor/host de HAProxy.
  // Next.js no llama directamente al Gateway, sino a HAProxy (puerto 80).
  // Luego, mediante la regla 'path_beg' definida en 'haproxy.cfg' (ej: /clientes/),
  // HAProxy reconoce el prefijo del recurso y redirige la petición a 'ms-gateway:8080',
  // donde Spring Cloud Gateway aplicará el 'RewritePath' según 'application.properties'.
  // Ej: /api/obras/5 -> http://haproxy:80/clientes/api/obras/5
  const incomingUrl = new URL(request.url);
  const targetPath = [haproxyPrefix, 'api', resource, ...rest].join('/');
  const targetUrl = new URL(`/${targetPath}${incomingUrl.search}`, haproxyBaseUrl);

  // El body solo tiene sentido en métodos que lo envían (POST/PUT/PATCH).
  const body = request.method !== 'GET'
    ? await request.text()
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

  const init: RequestInit = {
    method: request.method,
    headers,
    body,
  };

  // `fetch` del servidor hacia el gateway y devuelve la respuesta tal cual
  // (código de estado, body, headers) al navegador.
  const response = await fetch(targetUrl, init);

  return new NextResponse(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers: response.headers,
  });
}

// Exportamos el mismo manejador para todos los métodos HTTP. Next expone
// cada export como endpoint: export const GET → maneja peticiones GET, etc.
export const GET = proxyToGateway;
export const POST = proxyToGateway;
export const PUT = proxyToGateway;
export const PATCH = proxyToGateway;
export const DELETE = proxyToGateway;
