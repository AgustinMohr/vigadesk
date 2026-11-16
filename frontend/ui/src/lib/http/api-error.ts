// ==============================================================
// API ERROR — error HTTP estructurado
// --------------------------------------------------------------
// PROBLEMA que resuelve: el componente que captura con try/catch no
// puede distinguir "el token expiró" (401) de "no tenés permisos"
// (403) o "el recurso no existe" (404) si solo recibe un Error con
// un string. ApiError convierte el status HTTP en datos
// estructurados: status, mensaje amigable, path y el payload crudo
// del backend (details).
//
// CONCEPTOS CLAVE: clase tipada, getters (isUnauthorized /
// isForbidden / isNotFound), type guard (isApiError) y helper
// getErrorMessage para que los componentes muestren el mensaje
// descriptivo sin romper si el error no es nuestro.
// ==============================================================

/**
 * Error HTTP estructurado lanzado por http.ts.
 *
 * @typedef {Object} ApiError
 * @property {number} status - Código HTTP real (0 = error de red sin
 *   respuesta HTTP).
 * @property {string} message - Mensaje amigable para mostrar al usuario.
 * @property {unknown} [details] - Payload crudo que devolvió el backend
 *   (puede tener el mensaje de negocio real).
 * @property {string} [path] - Ruta relativa que falló (para debug).
 */
export class ApiError extends Error {
  readonly status: number;
  readonly path?: string;
  readonly details?: unknown;

  constructor (status: number, message: string, details?: unknown, path?: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.details = details;
    this.path = path;
  }

  /** 401 — credenciales inválidas o sesión expirada. */
  get isUnauthorized (): boolean {
    return this.status === 401;
  }

  /** 403 — el usuario no tiene permisos para la acción. */
  get isForbidden (): boolean {
    return this.status === 403;
  }

  /** 404 — el recurso solicitado no existe. */
  get isNotFound (): boolean {
    return this.status === 404;
  }

  /** Código 4xx — error del cliente (la petición estaba mal). */
  get isClientError (): boolean {
    return this.status >= 400 && this.status < 500;
  }

  /** Código 5xx — error del servidor (o red, status 0). */
  get isServerError (): boolean {
    return this.status >= 500;
  }
}

/**
 * Type guard: devuelve true solo si el error es una instancia de
 * ApiError. Con esto TypeScript estrecha el tipo en el catch.
 *
 * @param {unknown} error - Lo que capturó el try/catch.
 * @returns {boolean} True si es un ApiError.
 */
export function isApiError (error: unknown): error is ApiError {
  return error instanceof ApiError;
}

/**
 * Helper para los componentes: en el catch, si el error es un
 * ApiError muestra su mensaje amigable; si no (o si no es nuestro),
 * devuelve el fallback genérico.
 *
 * @param {unknown} error - Lo que capturó el try/catch.
 * @param {string} fallback - Mensaje genérico por defecto.
 * @returns {string} El mensaje a mostrar al usuario.
 */
export function getErrorMessage (error: unknown, fallback: string): string {
  return isApiError(error) ? error.message : fallback;
}
