// ==============================================================
// SERVICE DE PEDIDOS (mismo patrón que obraService.ts)
// --------------------------------------------------------------
// Tipos: un pedido tiene cliente + obra + lista de detalle (productos
// con cantidad/precio/descuento). `SearchParams` incluye `clienteId`
// para que el usuario CLIENTE vea solo los pedidos de SU empresa
// (el frontend filtra por clienteId al llamar a search()).
// ==============================================================
import { http } from './http';

// Info mínima del cliente/obra/producto dentro del pedido
export type ClienteInfo = {
  id: number;
  nombre: string;
  correoElectronico?: string;
  cuit?: string;
};

export type ObraInfo = {
  id: number;
  direccion: string;
};

export type ProductoInfo = {
  id: number;
  nombre?: string;
  descripcion?: string;
  precio?: number;
};

export type DetallePedido = {
  producto: ProductoInfo;
  cantidad: number;
  precioUnitario?: number;
  descuento?: number;
  precioFinal?: number;
};

export type Pedido = {
  id?: string; // MongoDB → el id es un string, no number
  fecha?: string;
  numeroPedido?: number;
  usuario?: string;
  observaciones?: string;
  cliente: ClienteInfo;
  obra?: ObraInfo;
  total?: number;
  estado: string;
  detalle: DetallePedido[];
};

export type SearchParams = {
  clienteNombre?: string;
  estado?: string;
  clienteId?: number;
};

const basePath = '/api/pedidos';

// Arma la query string (?clienteId=1&estado=...) de forma segura
// (URLSearchParams escapa caracteres especiales).
function buildSearchQuery (params: SearchParams): string {
  const query = new URLSearchParams();
  if (params.clienteNombre) query.set('clienteNombre', params.clienteNombre);
  if (params.estado) query.set('estado', params.estado);
  if (params.clienteId != null) query.set('clienteId', String(params.clienteId));
  const qs = query.toString();
  return qs ? `?${qs}` : '';
}

export const pedidoService = {
  getAll: () => http.get<Pedido[]>(basePath),
  getById: (id: string) => http.get<Pedido>(`${basePath}/${id}`),
  create: (pedido: Partial<Pedido>) => http.post<Pedido>(basePath, pedido),
  update: (id: string, pedido: Partial<Pedido>) =>
    http.put<Pedido>(`${basePath}/${id}`, pedido),
  delete: (id: string) => http.delete<void>(`${basePath}/${id}`),
  search: (params: SearchParams) =>
    http.get<Pedido[]>(`${basePath}/search${buildSearchQuery(params)}`),
  updateEstado: (id: string, estado: string) =>
    http.patch<Pedido>(`${basePath}/${id}/estado`, { estado }),
};
