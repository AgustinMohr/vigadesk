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
  const response = await fetch(path, {
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
    // Desempaqueta el resto de opciones recibidas (method, body, signal, etc.).
    ...options,
  });

  // 3. Manejo de Sesión Expirada (HTTP 401 Unauthorized):
  // Solo se activa si la respuesta es 401 Y además teníamos un token enviado previamente.
  // (Si recibes 401 sin token, significa simplemente que fallaron las credenciales de un Login).
  if (response.status === 401 && token) {
    handleUnauthorized();
  }

  // 4. Manejo de Errores HTTP (Códigos 4xx o 5xx):
  // `response.ok` es un booleano de la Fetch API nativa que es `true` solo si el código es 200-299.
  if (!response.ok) {
    // Lanza una excepción que interrumpe la función y podrá ser capturada por un try/catch en el service o componente.
    throw new Error(`Error HTTP ${response.status} al llamar ${path}`);
  }

  // 5. Manejo especial de HTTP 204 (No Content):
  // Ocurre frecuentemente en peticiones `DELETE` o `PUT` que fueron exitosas pero no devuelven un cuerpo JSON.
  // Si intentáramos ejecutar `response.json()` sobre una respuesta 204, JS lanzaría un error de sintaxis ("Unexpected end of JSON input").
  if (response.status === 204) {
    return undefined as T;
  }

  // 6. Deserialización del cuerpo de la respuesta:
  // Convierte los bytes del cuerpo JSON recibido a un objeto de JavaScript y lo castea al tipo dinámico `<T>`.
  return response.json() as Promise<T>;
}

/**
 * Objeto exportado con métodos utilitarios para cada verbo HTTP tradicional.
 * Facilita el uso en los servicios sin tener que configurar manualmente el método o el JSON.stringify().
 */
export const http = {
  // Petición GET: Consulta de recursos
  get: <T>(path: string) => request<T>(path),

  // Petición POST: Creación de un nuevo recurso
  post: <T>(path: string, body: unknown) =>
    request<T>(path, {
      method: 'POST',
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
};
