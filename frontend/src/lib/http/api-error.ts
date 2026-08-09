// ==============================================================
// API ERROR (tipo de datos)
// Define la FORMA que tiene un error devuelto por el backend.
// En TypeScript, una "interfaz" describe qué campos esperás que
// tenga un objeto, sin implementar nada. El backend responde errores
// con este formato: timestamp (cuándo), message (qué pasó),
// detalle (más info) y status (código HTTP 4xx/5xx).
// Se usa en services/http.ts para tipar el error al hacer fetch.
// ==============================================================

export interface ApiError {

  timestamp: string;

  message: string;

  detalle: string;

  status: number;

}
