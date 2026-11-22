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
  /** Código estable del backend (NOT_FOUND, CONFLICT, STOCK_INSUFICIENTE, VALIDATION_ERROR...). */
  readonly code?: string;
  /** Errores por campo cuando el backend rechazó la validación (code VALIDATION_ERROR). */
  readonly fieldErrors?: Record<string, string>;

  constructor (
    status: number,
    message: string,
    details?: unknown,
    path?: string,
    code?: string,
    fieldErrors?: Record<string, string>
  ) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.details = details;
    this.path = path;
    this.code = code;
    this.fieldErrors = fieldErrors;
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

/**
 * Cuerpo de error de los microservicios (formato único del backend).
 */
export type ErrorBody = {
  code?: string;
  message?: string;
  errors?: Record<string, string>;
};

/**
 * Lee el formato de error del backend `{ timestamp, status, code, message, path, errors? }` desde
 * un valor desconocido. Ignora cualquier campo que no tenga el tipo esperado, así un cuerpo
 * inesperado (HTML de un proxy, texto) no rompe el manejo de errores.
 *
 * @param {unknown} details - El JSON parseado de la respuesta, o undefined.
 * @returns {ErrorBody} Los campos reconocidos; vacío si no es el formato esperado.
 */
export function parseErrorBody (details: unknown): ErrorBody {
  if (typeof details !== 'object' || details === null) return {};
  const raw = details as Record<string, unknown>;
  const body: ErrorBody = {};
  if (typeof raw.code === 'string') body.code = raw.code;
  if (typeof raw.message === 'string' && raw.message.trim() !== '') body.message = raw.message;
  if (typeof raw.errors === 'object' && raw.errors !== null) {
    const errors: Record<string, string> = {};
    for (const [campo, detalle] of Object.entries(raw.errors as Record<string, unknown>)) {
      if (typeof detalle === 'string') errors[campo] = detalle;
    }
    if (Object.keys(errors).length > 0) body.errors = errors;
  }
  return body;
}
