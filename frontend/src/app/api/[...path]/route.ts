import { NextRequest, NextResponse } from "next/server";

// Proxy del frontend hacia el Spring Gateway.
// Captura /api/* y reenvía al backend, mapeando recursos a prefijos de ruta.
const gatewayBaseUrl =
  process.env.SPRING_GATEWAY_URL ?? "http://localhost:3080";

// Mapeo de recursos: "obras" vive bajo el gateway prefix "clientes"
const routePrefixes: Record<string, string> = {
  clientes: "clientes",
  obras: "clientes",
  productos: "productos",
  categorias: "productos",
  pedidos: "pedidos",
};

type RouteContext = {
  params: Promise<{
    path: string[];
  }>;
};

async function proxyToGateway(request: NextRequest, context: RouteContext) {
  const { path } = await context.params;
  const [resource, ...rest] = path;
  const gatewayPrefix = routePrefixes[resource];

  if (!gatewayPrefix) {
    return NextResponse.json(
      { message: `Ruta API no configurada: ${resource}` },
      { status: 404 },
    );
  }

  // Reconstruye la URL target: /{prefix}/api/{resource}/{rest}
  const incomingUrl = new URL(request.url);
  const targetPath = [gatewayPrefix, "api", resource, ...rest].join("/");
  const targetUrl = new URL(`/${targetPath}${incomingUrl.search}`, gatewayBaseUrl);

  const body = request.method !== "GET" && request.method !== "HEAD"
    ? await request.text()
    : undefined;

  const headers: Record<string, string> = {
    "content-type": request.headers.get("content-type") ?? "application/json",
  };

  const init: RequestInit = {
    method: request.method,
    headers,
    body,
  };

  const response = await fetch(targetUrl, init);

  return new NextResponse(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers: response.headers,
  });
}

// Soporta todos los métodos HTTP
export const GET = proxyToGateway;
export const POST = proxyToGateway;
export const PUT = proxyToGateway;
export const PATCH = proxyToGateway;
export const DELETE = proxyToGateway;
