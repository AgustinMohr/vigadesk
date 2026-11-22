import { ApiError, parseErrorBody } from '@/lib/http/api-error';

// ==============================================================
// CLIENTE HTTP GENÉRICO (la base de todos los services)
// --------------------------------------------------------------
// Toda llamada al backend pasa por acá. Las funciones usan `fetch` (API
// nativa del navegador), envuelta con:
//  - async/await: el `await` pausa hasta que el servidor responda.
//  - Genéricos <T>: el tipo de la respuesta se define donde se usa.
//  - Manejo de errores: si el backend responde con un código de error
//    (4xx/5xx), `response.ok` es false y lanzamos una excepción con
//    un mensaje claro que el componente captura con try/catch.
// NOTA: las rutas son RELATIVAS (ej: "/api/obras") → pasan por el proxy
// de Next.js (route.ts) y evitan problemas de CORS.
//
// AUTENTICACIÓN JWT: si existe un token guardado en localStorage, se
// envía como "Authorization: Bearer <token>" en cada request. Si el
// backend responde 401 (token expirado/inválido), se limpia la sesión
// y se redirige al login.
// ==============================================================

/**
 * Función auxiliar para obtener el token JWT desde el almacenamiento local.
 *
 * @returns {string | null} El token guardado o `null` si no existe o si se ejecuta en Node.js/Servidor.
 */
function getToken (): string | null {
  // `window` es un objeto global exclusivo de los navegadores.
  // Next.js ejecuta código tanto en el servidor (SSR) como en el cliente.
  // En el servidor `window` no existe (es `undefined`), por lo que intentar acceder a `localStorage`
  // provocaría un error fatal ("localStorage is not defined").
  if (typeof window === 'undefined') return null;

  // En el navegador, busca la clave 'auth_token' persistida en el disco local del cliente.
  return localStorage.getItem('auth_token');
}

/**
 * Limpia la sesión expirada del almacenamiento del cliente y redirige al usuario hacia el Login.
 */
function handleUnauthorized (): void {
  // Salvaguarda para asegurar que el código se ejecute exclusivamente en el navegador.
  if (typeof window === 'undefined') return;

  // Remueve las credenciales y datos del usuario almacenados localmente.
  localStorage.removeItem('auth_token');
  localStorage.removeItem('auth_user');

  // Redirige al login utilizando `window.location.href` (forzando una navegación limpia),
  // pero solo si el usuario no se encuentra ya dentro de la pantalla de `/login`
  // (para evitar bucles infinitos de re-direccionamiento).
  if (!window.location.pathname.startsWith('/login')) {
    window.location.href = '/login';
  }
}

/**
 * Devuelve un mensaje amigable y descriptivo según el status HTTP.
 * Es el texto que verá el usuario cuando un request falla.
 *
 * @param {number} status - Código HTTP de la respuesta.
 * @param {string} path - Ruta que falló (para el mensaje genérico).
 * @returns {string} Mensaje pensado para mostrar en la UI.
 */
function describeStatus (status: number, path: string): string {
  switch (status) {
    case 400:
      return 'La solicitud no es válida. Revisá los datos enviados.';
    case 401:
      return 'Tu sesión expiró o las credenciales no son válidas.';
    case 403:
      return 'No tenés permisos para realizar esta acción.';
    case 404:
      return 'El recurso solicitado no existe o fue eliminado.';
    case 409:
      return 'El recurso ya existe o hay un conflicto de datos.';
    case 413:
      return 'El archivo o los datos enviados son demasiado grandes.';
    case 422:
      return 'No se pudo procesar la solicitud. Revisá los campos.';
    case 502:
      return 'El servidor no está disponible en este momento.';
    case 503:
      return 'El servicio está temporalmente fuera de servicio.';
    case 504:
      return 'El servidor tardó demasiado en responder. Intentá de nuevo.';
    default:
      if (status >= 500) {
        return 'Ocurrió un error en el servidor. Intentá de nuevo más tarde.';
      }
      return `Error HTTP ${status} al llamar ${path}`;
  }
}

/**
 * Intenta leer el payload JSON de una respuesta de error. El backend
 * puede responder con `{ message: "..." }` (RestControllerException);
 * ese dato viaja como `details` del ApiError para debug y trazabilidad.
 *
 * @param {Response} response - La respuesta HTTP que no fue OK.
 * @returns {Promise<unknown>} El JSON parseado, o undefined si no es JSON.
 */
async function parseErrorDetails (response: Response): Promise<unknown> {
  try {
    return await response.json();
  } catch {
    return undefined; // sin body o no era JSON
  }
}

/**
 * Arma el ApiError de una respuesta no OK. Los microservicios responden con
 * `{ timestamp, status, code, message, path, errors? }`:
 *  - 4xx (salvo 401): se muestra el `message` del servidor, que es texto pensado para el usuario
 *    (y, si es una validación, los errores por campo).
 *  - 401 y 5xx: texto genérico por status; un 5xx nunca trae detalle interno y no se muestra.
 * Si el cuerpo no tiene ese formato (por ejemplo un error del proxy), se usa el genérico.
 *
 * @param {number} status - Código HTTP de la respuesta.
 * @param {string} path - Ruta que falló.
 * @param {unknown} details - Cuerpo JSON de la respuesta, si lo había.
 * @returns {ApiError} El error estructurado.
 */
function buildApiError (status: number, path: string, details: unknown): ApiError {
  const body = parseErrorBody(details);
  const usarMensajeDelServidor = status >= 400 && status < 500 && status !== 401 && body.message;
  const message = usarMensajeDelServidor
    ? withFieldErrors(body.message!, body.errors)
    : describeStatus(status, path);
  return new ApiError(status, message, details, path, body.code, body.errors);
}

function withFieldErrors (message: string, errors?: Record<string, string>): string {
  if (!errors) return message;
  const lista = Object.entries(errors).map(([campo, detalle]) => `${campo}: ${detalle}`);
  return lista.length > 0 ? `${message} (${lista.join('; ')})` : message;
}

/**
 * Función central asíncrona que realiza las peticiones HTTP mediante `fetch`.
 * Utiliza Genéricos `<T>` para tipar automáticamente el JSON que devuelve el servidor.
 *
 * @param {string} path - La ruta relativa de la API (ej: "/api/obras/5").
 * @param {RequestInit} [options] - Opciones adicionales de la Fetch API (método, headers personalizados, body).
 * @returns {Promise<T>} Promesa con los datos ya deserializados al tipo `T`.
 */
async function request<T> (path: string, options?: RequestInit): Promise<T> {
  // 1. Obtiene el token guardado en la sesión (si existe)
  const token = getToken();

  // 2. Ejecuta la petición HTTP hacia el Proxy de Next.js (o el Backend)
  // `await` detiene la ejecución hasta que la red devuelve las cabeceras de respuesta.
  // Si `fetch` rechaza (sin respuesta HTTP), es un problema de red: lo
  // convertimos en un ApiError con status 0 y mensaje descriptivo.
  let response: Response;
  try {
    response = await fetch(path, {
      // Desempaqueta method, body, signal y demás opciones recibidas.
      ...options,
      headers: {
        // Garantiza que el backend interprete que le estamos enviando un cuerpo JSON.
        'Content-Type': 'application/json',

        // Mezcla de objetos mediante el operador Spread (`...`):
        // Si `token` existe, fusiona `{ Authorization: 'Bearer <token>' }`.
        // Si es `null`, fusiona un objeto vacío `{}` (para rutas públicas como /login).
        ...(token ? { Authorization: `Bearer ${token}` } : {}),

        // Permite sobreescribir o agregar headers adicionales desde las `options` si se requiere.
        ...options?.headers,
      },
    });
  } catch (err) {
    throw new ApiError(
      0,
      'No se pudo conectar con el servidor. Intentá de nuevo en unos segundos.',
      err,
      path
    );
  }

  // 3. Manejo de Sesión Expirada (HTTP 401 Unauthorized):
  // Solo se activa si la respuesta es 401 Y además teníamos un token enviado previamente.
  // (Si recibes 401 sin token, significa simplemente que fallaron las credenciales de un Login).
  if (response.status === 401 && token) {
    handleUnauthorized();
  }

  // 4. Manejo especial de HTTP 204 (No Content):
  // Ocurre frecuentemente en peticiones `DELETE` o `PUT` que fueron exitosas pero no devuelven un cuerpo JSON.
  // Si intentáramos ejecutar `response.json()` sobre una respuesta 204, JS lanzaría un error de sintaxis ("Unexpected end of JSON input").
  if (response.status === 204) {
    return undefined as T;
  }

  // 5. Manejo de Errores HTTP (Códigos 4xx o 5xx):
  // `response.ok` es un booleano de la Fetch API nativa que es `true` solo si el código es 200-299.
  // Lanzamos un ApiError estructurado: el componente captura con try/catch y
  // puede discriminar la causa (401 vs 403 vs 404...) con los getters, o
  // mostrar el mensaje amigable con getErrorMessage.
  if (!response.ok) {
    const details = await parseErrorDetails(response);
    throw buildApiError(response.status, path, details);
  }

  // 6. Deserialización del cuerpo de la respuesta:
  // Convierte los bytes del cuerpo JSON recibido a un objeto de JavaScript y lo castea al tipo dinámico `<T>`.
  return response.json() as Promise<T>;
}

/** Tamaño de página que se pide al backend (su máximo permitido). */
const TAMANIO_PAGINA = 100;
/** Tope de páginas por consulta: evita un bucle infinito si el backend ignorara `page`. */
const MAX_PAGINAS = 500;

/**
 * Los listados del backend están paginados (`page`/`size`, máximo 100 por página). Las tablas del
 * frontend filtran y ordenan en memoria, así que este helper pide las páginas una a una hasta
 * traer todo el conjunto, sin que ninguna request supere el tamaño máximo.
 *
 * @param {string} path - Ruta del listado, con o sin query string.
 * @returns {Promise<T[]>} Todos los elementos.
 */
async function getAllPages<T> (path: string): Promise<T[]> {
  const separador = path.includes('?') ? '&' : '?';
  const todos: T[] = [];
  for (let page = 0; page < MAX_PAGINAS; page++) {
    const pagina = await request<T[]>(`${path}${separador}page=${page}&size=${TAMANIO_PAGINA}`);
    todos.push(...pagina);
    if (pagina.length < TAMANIO_PAGINA) break;
  }
  return todos;
}

/**
 * Objeto exportado con métodos utilitarios para cada verbo HTTP tradicional.
 * Facilita el uso en los servicios sin tener que configurar manualmente el método o el JSON.stringify().
 */
export const http = {
  // Petición GET: Consulta de recursos
  get: <T>(path: string) => request<T>(path),

  // GET de un listado paginado: trae todas las páginas (ver getAllPages)
  getAll: <T>(path: string) => getAllPages<T>(path),

  // Petición POST: Creación de un nuevo recurso
  post: <T>(path: string, body: unknown, headers?: Record<string, string>) =>
    request<T>(path, {
      method: 'POST',
      headers,
      body: JSON.stringify(body), // Convierte el objeto JavaScript recibido a texto plano en formato JSON
    }),

  // Petición PUT: Reemplazo/Actualización completa de un recurso
  put: <T>(path: string, body: unknown) =>
    request<T>(path, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),

  // Petición PATCH: Actualización parcial de un recurso
  patch: <T>(path: string, body: unknown) =>
    request<T>(path, {
      method: 'PATCH',
      body: JSON.stringify(body),
    }),

  // Petición DELETE: Eliminación de un recurso
  delete: <T>(path: string) =>
    request<T>(path, {
      method: 'DELETE',
    }),

  // Descarga binaria (PDF) y dispara el guardado en el navegador.
  download: async (path: string, filename: string): Promise<void> => {
    const token = getToken();
    let response: Response;
    try {
      response = await fetch(path, {
        headers: token ? { Authorization: `Bearer ${token}` } : undefined,
      });
    } catch (err) {
      throw new ApiError(0, 'No se pudo conectar con el servidor.', err, path);
    }
    if (response.status === 401 && token) {
      handleUnauthorized();
    }
    if (!response.ok) {
      const details = await parseErrorDetails(response);
      throw buildApiError(response.status, path, details);
    }
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  },

  // Upload multipart: no fija Content-Type para que el navegador agregue el boundary.
  upload: async <T>(path: string, formData: FormData): Promise<T> => {
    const token = getToken();
    let response: Response;
    try {
      response = await fetch(path, {
        method: 'POST',
        headers: token ? { Authorization: `Bearer ${token}` } : undefined,
        body: formData,
      });
    } catch (err) {
      throw new ApiError(0, 'No se pudo conectar con el servidor.', err, path);
    }

    if (response.status === 401 && token) {
      handleUnauthorized();
    }
    if (!response.ok) {
      const details = await parseErrorDetails(response);
      throw buildApiError(response.status, path, details);
    }
    return response.json() as Promise<T>;
  },
};
