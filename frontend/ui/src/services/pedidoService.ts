// ==============================================================
// SERVICE DE PEDIDOS — la capa que habla con la API
// --------------------------------------------------------------
// Mismo patrón de arquitectura que obraService.ts (ver ahí la teoría
// completa del patrón de Services). Particularidades de pedidos:
//  - El id es un STRING (MongoDB usa ObjectId, no enteros de SQL).
//  - Un pedido agrupa: cliente + obra + una lista de detalle (productos
//    con cantidad/precio/descuento).
//  - search() con filtros: por nombre del cliente, por estado y/o por
//    clienteId. Este último es CLAVE: permite que el usuario con rol
//    CLIENTE vea SOLO los pedidos de SU empresa (el frontend inyecta su
//    clienteId al llamar a search()).
//
// NOTA: las rutas son RELATIVAS → pasan por el proxy de Next.js que las
// traduce al microservicio "pedidos".
// ==============================================================

import { http } from './http';

/** Info mínima del cliente dentro de un pedido (denormalizada). */
export type ClienteInfo = {
  id: number;
  nombre: string;
  correoElectronico?: string;
  cuit?: string;
};

/** Info mínima de la obra a la que pertenece el pedido. */
export type ObraInfo = {
  id: number;
  direccion: string;
};

/** Info mínima del producto dentro de una línea de detalle. */
export type ProductoInfo = {
  id: number;
  nombre?: string;
  descripcion?: string;
  unidadMedida?: string;
  precio?: number;
  descuentoPromocional?: number;
};

/**
 * Línea de detalle del pedido: un producto con su cantidad y precios.
 * `cantidadPendiente` la calcula el backend a partir de lo entregado.
 */
export type DetallePedido = {
  lineaId: string;
  producto: ProductoInfo;
  cantidad: number;
  cantidadEntregada?: number;
  cantidadPendiente?: number;
  precioUnitario?: number;
  descuento?: number;   // % de descuento (0-100)
  precioFinal?: number; // precio ya con descuento aplicado
};

export type EntregaItem = {
  lineaId: string;
  idProducto: number;
  productoNombre: string;
  cantidad: number;
};

export type Entrega = {
  id: string;
  numeroRemito: number;
  fecha: string;
  receptor: string;
  observaciones?: string;
  items: EntregaItem[];
};

export type EntregaInput = {
  receptor: string;
  observaciones?: string;
  items: Array<{ lineaId: string; cantidad: number }>;
};

/**
 * Pedido completo tal como lo devuelve la API. Observá que `id?` es string:
 * MongoDB genera ObjectId (24 caracteres hex) que pasan al frontend como
 * texto, no como número. Por eso los métodos usan id: string.
 */
export type Pedido = {
  id?: string; // MongoDB → el id es un string, no number
  fecha?: string;
  numeroPedido?: number;
  usuario?: string; // quién cargó el pedido
  observaciones?: string;
  cliente: ClienteInfo;
  obra?: ObraInfo;
  total?: number;
  estado: string;
  detalle: DetallePedido[];
  entregas?: Entrega[];
  auditoria?: Array<{
    fecha: string;
    actor: string;
    accion: string;
    detalle: string;
  }>;
};

/**
 * Filtros opcionales para search(). `clienteId` es el que usa el usuario
 * CLIENTE para ver solo sus propios pedidos (el resto de campos lo usan
 * ADMIN en la pantalla de pedidos).
 */
export type SearchParams = {
  clienteNombre?: string;
  estado?: string;
  clienteId?: number;
};

// Prefijo común de todas las rutas de pedidos.
const basePath = '/api/pedidos';

/**
 * Construye la query string (?clienteNombre=x&estado=...) a partir de los
 * filtros dados, agregando solo los que vienen definidos.
 *
 * @param {SearchParams} params - Filtros opcionales a traducir a query string.
 * @returns {string} La query string con "?" inicial, o "" si no hay filtros.
 */
function buildSearchQuery (params: SearchParams): string {
  // URLSearchParams escapa caracteres especiales automáticamente (seguridad
  // y correctitud: un nombre con espacios o "&" no rompería la URL).
  const query = new URLSearchParams();
  if (params.clienteNombre) query.set('clienteNombre', params.clienteNombre);
  if (params.estado) query.set('estado', params.estado);

  // Ojo con `!= null`: cubre null Y undefined (no discrimina entre ambos),
  // pero NO convierte 0 a undefined (0 es un clienteId válido).
  if (params.clienteId != null) query.set('clienteId', String(params.clienteId));

  const qs = query.toString();
  return qs ? `?${qs}` : '';
}

/**
 * Objeto con las operaciones del CRUD + búsqueda + cambio de estado.
 */
/**
 * Cuerpo de alta de un pedido (contrato de POST /api/pedidos). Solo lo que decide quien
 * pide; el servidor fija estado, total, precios y datos del cliente. `clienteId` lo usa un
 * ADMIN; para un CLIENTE el backend lo toma del token.
 */
export type CrearPedidoInput = {
  clienteId?: number;
  obraId: number;
  observaciones?: string;
  lineas: { productoId: number; cantidad: number }[];
};

export const pedidoService = {
  // GET /api/pedidos → lista completa de pedidos
  getAll: () => http.get<Pedido[]>(basePath),

  // POST /api/pedidos → crea un pedido con su detalle.
  // El Idempotency-Key evita duplicados si el navegador reintenta.
  create: (pedido: CrearPedidoInput, idempotencyKey?: string) =>
    http.post<Pedido>(
      basePath,
      pedido,
      idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined
    ),

  // DELETE /api/pedidos/:id → elimina; HTTP 204 (sin body → <void>)
  delete: (id: string) => http.delete<void>(`${basePath}/${id}`),

  // GET /api/pedidos/search?filtros → búsqueda filtrada por query string
  search: (params: SearchParams) =>
    http.get<Pedido[]>(`${basePath}/search${buildSearchQuery(params)}`),

  // PATCH /api/pedidos/:id/estado → actualiza SOLO el estado del pedido
  updateEstado: (id: string, estado: string) =>
    http.patch<Pedido>(`${basePath}/${id}/estado`, { estado }),

  // POST /api/pedidos/:id/reintentar-reserva → ADMIN: reintenta reservar stock
  // de un pedido ACEPTADO (pendiente de stock). 409 si sigue sin stock.
  reintentarReserva: (id: string) =>
    http.post<Pedido>(`${basePath}/${id}/reintentar-reserva`, {}),

  // GET /api/pedidos/:id/entregas → remitos registrados
  getEntregas: (id: string) =>
    http.get<Entrega[]>(`${basePath}/${id}/entregas`),

  // POST /api/pedidos/:id/entregas → registra entrega parcial o total
  createEntrega: (id: string, entrega: EntregaInput) =>
    http.post<Pedido>(`${basePath}/${id}/entregas`, entrega),
};
