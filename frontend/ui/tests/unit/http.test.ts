import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { http } from '@/services/http';
import { ApiError, getErrorMessage, isApiError, parseErrorBody } from '@/lib/http/api-error';

function respuesta (status: number, body?: unknown, headers: Record<string, string> = {}): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json', ...headers },
  });
}

const fetchMock = vi.fn();

/** Espera que la promesa falle y devuelve el ApiError; si no falla, el test falla. */
async function fallo (promesa: Promise<unknown>): Promise<ApiError> {
  try {
    await promesa;
  } catch (e) {
    return e as ApiError;
  }
  throw new Error('se esperaba que la llamada fallara');
}

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('http: peticiones', () => {
  it('GET devuelve el JSON y manda Content-Type', async () => {
    fetchMock.mockResolvedValue(respuesta(200, [{ id: 1 }]));

    const data = await http.get<{ id: number }[]>('/api/obras');

    expect(data).toEqual([{ id: 1 }]);
    const [ruta, init] = fetchMock.mock.calls[0];
    expect(ruta).toBe('/api/obras');
    expect(init.headers['Content-Type']).toBe('application/json');
  });

  it('envia el token como Bearer cuando hay sesion', async () => {
    localStorage.setItem('auth_token', 'abc.def');
    fetchMock.mockResolvedValue(respuesta(200, {}));

    await http.get('/api/obras');

    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer abc.def');
  });

  it('sin token no manda Authorization', async () => {
    fetchMock.mockResolvedValue(respuesta(200, {}));

    await http.get('/api/obras');

    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBeUndefined();
  });

  it('POST serializa el cuerpo y permite cabeceras extra', async () => {
    fetchMock.mockResolvedValue(respuesta(200, { id: 7 }));

    await http.post('/api/pedidos', { obraId: 3 }, { 'Idempotency-Key': 'k-1' });

    const init = fetchMock.mock.calls[0][1];
    expect(init.method).toBe('POST');
    expect(init.body).toBe('{"obraId":3}');
    expect(init.headers['Idempotency-Key']).toBe('k-1');
  });

  it('PUT, PATCH y DELETE usan su metodo', async () => {
    fetchMock.mockImplementation(async () => respuesta(200, {}));

    await http.put('/x', {});
    await http.patch('/x', {});
    await http.delete('/x');

    expect(fetchMock.mock.calls.map(c => c[1].method)).toEqual(['PUT', 'PATCH', 'DELETE']);
  });

  it('204 devuelve undefined sin intentar parsear', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    await expect(http.delete('/api/obras/1')).resolves.toBeUndefined();
  });
});

describe('http: getAll recorre las paginas', () => {
  const pagina = (desde: number, n: number) => Array.from({ length: n }, (_, i) => ({ id: desde + i }));

  it('pide paginas de 100 hasta que una viene incompleta', async () => {
    fetchMock
      .mockResolvedValueOnce(respuesta(200, pagina(0, 100)))
      .mockResolvedValueOnce(respuesta(200, pagina(100, 100)))
      .mockResolvedValueOnce(respuesta(200, pagina(200, 30)));

    const todos = await http.getAll<{ id: number }>('/api/clientes');

    expect(todos).toHaveLength(230);
    expect(fetchMock.mock.calls.map(c => c[0])).toEqual([
      '/api/clientes?page=0&size=100',
      '/api/clientes?page=1&size=100',
      '/api/clientes?page=2&size=100',
    ]);
  });

  it('agrega page y size con & si la ruta ya tiene query', async () => {
    fetchMock.mockResolvedValue(respuesta(200, []));

    await http.getAll('/api/pedidos/search?estado=ACEPTADO');

    expect(fetchMock.mock.calls[0][0]).toBe('/api/pedidos/search?estado=ACEPTADO&page=0&size=100');
  });

  it('una lista vacia termina en una sola llamada', async () => {
    fetchMock.mockResolvedValue(respuesta(200, []));

    await expect(http.getAll('/api/obras')).resolves.toEqual([]);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('propaga el error si una pagina falla', async () => {
    fetchMock
      .mockResolvedValueOnce(respuesta(200, pagina(0, 100)))
      .mockResolvedValueOnce(respuesta(503, { code: 'SERVICE_UNAVAILABLE' }));

    await expect(http.getAll('/api/clientes')).rejects.toMatchObject({ status: 503 });
  });
});

describe('http: errores', () => {
  it('401 con sesion limpia el almacenamiento y redirige al login', async () => {
    localStorage.setItem('auth_token', 'vencido');
    localStorage.setItem('auth_user', '{}');
    history.pushState({}, '', '/login'); // ya en /login: no navega (jsdom no implementa navegacion)
    fetchMock.mockResolvedValue(respuesta(401, { message: 'x' }));

    await expect(http.get('/api/obras')).rejects.toMatchObject({ status: 401, isUnauthorized: true });

    expect(localStorage.getItem('auth_token')).toBeNull();
    expect(localStorage.getItem('auth_user')).toBeNull();
  });

  it('401 sin token (login fallido) no toca la sesion y usa el texto generico', async () => {
    localStorage.setItem('auth_user', '{"email":"a"}');
    fetchMock.mockResolvedValue(respuesta(401, { message: 'Credenciales invalidas del servidor' }));

    const error = await fallo(http.post('/api/usuarios/login', {}));

    expect(error).toBeInstanceOf(ApiError);
    expect(error.message).toBe('Tu sesión expiró o las credenciales no son válidas.');
    expect(localStorage.getItem('auth_user')).toBe('{"email":"a"}');
  });

  it('4xx muestra el mensaje del servidor con los errores por campo', async () => {
    fetchMock.mockResolvedValue(respuesta(400, {
      status: 400,
      code: 'VALIDATION_ERROR',
      message: 'Datos invalidos',
      errors: { nombre: 'es obligatorio', email: 'no es valido' },
    }));

    const error = await fallo(http.post('/api/clientes', {}));

    expect(error.message).toBe('Datos invalidos (nombre: es obligatorio; email: no es valido)');
    expect(error.code).toBe('VALIDATION_ERROR');
    expect(error.fieldErrors).toEqual({ nombre: 'es obligatorio', email: 'no es valido' });
    expect(error.isClientError).toBe(true);
  });

  it('409 de conflicto usa el mensaje del servidor', async () => {
    fetchMock.mockResolvedValue(respuesta(409, { code: 'CONFLICT', message: 'Ya existe un cliente con ese correo' }));

    const error = await fallo(http.post('/api/clientes', {}));

    expect(error.message).toBe('Ya existe un cliente con ese correo');
    expect(error.status).toBe(409);
  });

  it('5xx nunca muestra el detalle del servidor', async () => {
    fetchMock.mockResolvedValue(respuesta(500, { message: 'NullPointerException en Foo.java:12' }));

    const error = await fallo(http.get('/api/obras'));

    expect(error.message).toBe('Ocurrió un error en el servidor. Intentá de nuevo más tarde.');
    expect(error.isServerError).toBe(true);
  });

  it.each([
    [403, 'No tenés permisos para realizar esta acción.'],
    [404, 'El recurso solicitado no existe o fue eliminado.'],
    [502, 'El servidor no está disponible en este momento.'],
    [503, 'El servicio está temporalmente fuera de servicio.'],
    [504, 'El servidor tardó demasiado en responder. Intentá de nuevo.'],
  ])('%i sin cuerpo usa el texto generico', async (status, texto) => {
    fetchMock.mockResolvedValue(new Response('no es json', { status }));

    const error = await fallo(http.get('/api/obras'));

    expect(error.message).toBe(texto);
  });

  it('un fallo de red se convierte en ApiError con status 0', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));

    const error = await fallo(http.get('/api/obras'));

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(0);
    expect(error.message).toContain('No se pudo conectar');
  });
});

describe('api-error', () => {
  it('parseErrorBody ignora cuerpos que no tienen el formato esperado', () => {
    expect(parseErrorBody(undefined)).toEqual({});
    expect(parseErrorBody('texto')).toEqual({});
    expect(parseErrorBody({ message: '   ', errors: { a: 1 } })).toEqual({});
    expect(parseErrorBody({ code: 'X', message: 'm', errors: { a: 'b', c: 3 } }))
      .toEqual({ code: 'X', message: 'm', errors: { a: 'b' } });
  });

  it('getErrorMessage usa el mensaje de un ApiError y el de respaldo para lo demas', () => {
    expect(getErrorMessage(new ApiError(400, 'malo'), 'respaldo')).toBe('malo');
    expect(getErrorMessage(new Error('x'), 'respaldo')).toBe('respaldo');
    expect(isApiError(new Error('x'))).toBe(false);
  });
});
