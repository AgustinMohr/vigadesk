import { NextRequest } from 'next/server';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DELETE, GET, PATCH, POST, PUT } from '@/app/api/[...path]/route';

const fetchMock = vi.fn();

function llamar (
  handler: typeof GET,
  ruta: string,
  init: { method?: string; headers?: Record<string, string>; body?: BodyInit } = {}
) {
  const request = new NextRequest(`http://localhost:3000${ruta}`, init);
  const path = new URL(request.url).pathname.replace(/^\/api\//, '').split('/');
  return handler(request, { params: Promise.resolve({ path }) });
}

beforeEach(() => {
  fetchMock.mockReset();
  fetchMock.mockResolvedValue(new Response('{"ok":true}', {
    status: 200,
    headers: { 'content-type': 'application/json', 'set-cookie': 'sesion=secreta', 'x-interno': 'no' },
  }));
  vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('proxy BFF: whitelist de rutas', () => {
  it.each([
    ['/api/clientes', 'http://haproxy/clientes/api/clientes'],
    ['/api/obras/5', 'http://haproxy/clientes/api/obras/5'],
    ['/api/usuarios/login', 'http://haproxy/clientes/api/usuarios/login'],
    ['/api/productos/3', 'http://haproxy/productos/api/productos/3'],
    ['/api/categorias', 'http://haproxy/productos/api/categorias'],
    ['/api/pedidos/abc/estado', 'http://haproxy/pedidos/api/pedidos/abc/estado'],
  ])('%s se reenvia a %s', async (ruta, destino) => {
    await llamar(GET, ruta);

    expect(String(fetchMock.mock.calls[0][0])).toBe(destino);
  });

  it('un recurso que no esta en la whitelist responde 404 y no llama al backend', async () => {
    const respuesta = await llamar(GET, '/api/actuator');

    expect(respuesta.status).toBe(404);
    expect(await respuesta.json()).toEqual({ message: 'Ruta API no configurada: actuator' });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('mantiene el query string', async () => {
    await llamar(GET, '/api/pedidos/search?estado=ACEPTADO&page=2&size=50');

    expect(String(fetchMock.mock.calls[0][0])).toBe(
      'http://haproxy/pedidos/api/pedidos/search?estado=ACEPTADO&page=2&size=50');
  });
});

describe('proxy BFF: cabeceras', () => {
  it('reenvia Authorization, Idempotency-Key y Accept', async () => {
    await llamar(GET, '/api/pedidos', {
      headers: { authorization: 'Bearer jwt', 'idempotency-key': 'k-9', accept: 'application/pdf' },
    });

    const headers = fetchMock.mock.calls[0][1].headers;
    expect(headers.authorization).toBe('Bearer jwt');
    expect(headers['idempotency-key']).toBe('k-9');
    expect(headers.accept).toBe('application/pdf');
  });

  it('no reenvia cabeceras que no estan en la lista blanca (X-User-*, Cookie)', async () => {
    await llamar(GET, '/api/pedidos', {
      headers: { 'x-user-rol': 'ADMIN', 'x-user-clienteid': '1', cookie: 'a=b', 'x-forwarded-for': '6.6.6.6' },
    });

    const headers = fetchMock.mock.calls[0][1].headers;
    expect(Object.keys(headers).sort()).toEqual(['content-type']);
  });

  it('solo devuelve al navegador el content-type de la respuesta', async () => {
    const respuesta = await llamar(GET, '/api/obras');

    expect(respuesta.headers.get('content-type')).toBe('application/json');
    expect(respuesta.headers.get('set-cookie')).toBeNull();
    expect(respuesta.headers.get('x-interno')).toBeNull();
  });

  it('copia el status del backend, tambien en los errores', async () => {
    fetchMock.mockResolvedValue(new Response('{"code":"CONFLICT"}', { status: 409 }));

    const respuesta = await llamar(POST, '/api/clientes', { method: 'POST', body: '{}' });

    expect(respuesta.status).toBe(409);
  });
});

describe('proxy BFF: metodos y cuerpo', () => {
  it('GET no envia cuerpo', async () => {
    await llamar(GET, '/api/obras');

    expect(fetchMock.mock.calls[0][1].body).toBeUndefined();
    expect(fetchMock.mock.calls[0][1].method).toBe('GET');
  });

  it.each([
    ['POST', POST],
    ['PUT', PUT],
    ['PATCH', PATCH],
  ])('%s reenvia el cuerpo tal cual', async (method, handler) => {
    await llamar(handler, '/api/clientes/1', {
      method,
      headers: { 'content-type': 'application/json' },
      body: '{"nombre":"Ñandú"}',
    });

    const init = fetchMock.mock.calls[0][1];
    expect(init.method).toBe(method);
    expect(new TextDecoder().decode(init.body)).toBe('{"nombre":"Ñandú"}');
  });

  it('DELETE usa su metodo', async () => {
    await llamar(DELETE, '/api/clientes/1', { method: 'DELETE' });

    expect(fetchMock.mock.calls[0][1].method).toBe('DELETE');
  });

  it('conserva el content-type de un multipart (importacion de CSV)', async () => {
    const form = new FormData();
    form.append('file', new Blob(['nombre\nA\n'], { type: 'text/csv' }), 'c.csv');
    const request = new NextRequest('http://localhost:3000/api/clientes/import', { method: 'POST', body: form });

    await POST(request, { params: Promise.resolve({ path: ['clientes', 'import'] }) });

    expect(fetchMock.mock.calls[0][1].headers['content-type']).toMatch(/^multipart\/form-data; boundary=/);
  });
});

describe('proxy BFF: limites y fallos', () => {
  it('rechaza un cuerpo de mas de 5 MB con 413 sin llamar al backend', async () => {
    const respuesta = await llamar(POST, '/api/clientes/import', {
      method: 'POST',
      headers: { 'content-length': String(5 * 1024 * 1024 + 1), 'content-type': 'text/csv' },
      body: 'x',
    });

    expect(respuesta.status).toBe(413);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('acepta un cuerpo justo en el limite', async () => {
    const respuesta = await llamar(POST, '/api/clientes/import', {
      method: 'POST',
      headers: { 'content-length': String(5 * 1024 * 1024), 'content-type': 'text/csv' },
      body: 'x',
    });

    expect(respuesta.status).toBe(200);
  });

  it('si el backend no responde devuelve 502', async () => {
    fetchMock.mockRejectedValue(new TypeError('fetch failed'));

    const respuesta = await llamar(GET, '/api/obras');

    expect(respuesta.status).toBe(502);
  });

  it('si el backend tarda demasiado aborta y devuelve 504', async () => {
    vi.useFakeTimers();
    fetchMock.mockImplementation((_url: URL, init: RequestInit) => new Promise((_resolve, reject) => {
      init.signal!.addEventListener('abort', () => {
        const error = new Error('aborted');
        error.name = 'AbortError';
        reject(error);
      });
    }));

    const pendiente = llamar(GET, '/api/obras');
    await vi.advanceTimersByTimeAsync(15_001);
    const respuesta = await pendiente;
    vi.useRealTimers();

    expect(respuesta.status).toBe(504);
  });
});
