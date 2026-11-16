// ==============================================================
// SERVICE DE CLIENTES — la capa que habla con la API
// --------------------------------------------------------------
// Mismo patrón de arquitectura que obraService.ts (ver ahí la teoría
// completa del patrón de Services). Este archivo solo expone el CRUD
// básico de clientes: la app no tiene búsqueda especial de clientes.
//
// NOTA: las rutas son RELATIVAS ("/api/clientes") → pasan por el proxy
// de Next.js (app/api/[...path]/route.ts) que las traduce a
// "/clientes/api/clientes" dentro del microservicio "clientes".
// ==============================================================

import { http } from './http';

/**
 * Forma de un cliente según el JSON que devuelve el backend.
 * Los campos con `?` son OPCIONALES: el backend los guarda como null/ausentes
 * si no se proporcionan (ej: un cliente puede no tener CUIT aún).
 * Se usa `Partial<Cliente>` en create/update para permitir enviar solo
 * un subconjunto de campos desde el formulario.
 *
 * @typedef {Object} Cliente
 * @property {number} [id] - Identificador numérico (autogenerado por el backend).
 * @property {string} nombre - Nombre o razón social del cliente (obligatorio).
 * @property {string} correoElectronico - Email de contacto (obligatorio).
 * @property {string} [cuit] - CUIT opcional; se valida solo si viene.
 * @property {number} [maximoDescubierto] - Límite de crédito del cliente.
 */
export type Cliente = {
  id?: number;
  nombre: string;
  correoElectronico: string;
  cuit?: string;
  maximoDescubierto?: number;
  saldoInicial?: number;
};

// Prefijo común de todas las rutas de clientes. La URL final siempre es
// relativa → el proxy (app/api/[...path]/route.ts) la redirige al gateway.
const basePath = '/api/clientes';

/**
 * Objeto con las operaciones del CRUD de clientes. Cada método mapea 1 a 1
 * a un endpoint REST del backend y tipa la respuesta con el genérico <T>.
 * Al llamarlo con `http.get<Cliente[]>` indicamos que la respuesta será un
 * array de Cliente, y TS lo infiere en el componente sin castings manuales.
 */
export const clienteService = {
  // GET /api/clientes → lista completa de clientes
  getAll: () => http.get<Cliente[]>(basePath),

  // POST /api/clientes → crea un cliente; el body puede ser parcial
  create: (cliente: Partial<Cliente>) => http.post<Cliente>(basePath, cliente),

  // PUT /api/clientes/:id → reemplazo completo del recurso con el id dado
  update: (id: number, cliente: Partial<Cliente>) =>
    http.put<Cliente>(`${basePath}/${id}`, cliente),

  // DELETE /api/clientes/:id → lo elimina; HTTP 204 sin cuerpo (por eso <void>)
  delete: (id: number) => http.delete<void>(`${basePath}/${id}`),
};
