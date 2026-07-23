// Servicio API para el CRUD de productos y búsqueda con filtros
// También expone getCategorias() para poblar el dropdown del formulario
import { http } from "./http";

export type Producto = {
  id?: number;
  codigo?: string;
  nombre: string;
  descripcion?: string;
  precio: number;
  stockActual: number;
  stockMinimo: number;
  descuentoPromocional?: number;
  categoria?: string;
};

export type SearchParams = {
  nombre?: string;
  codigo?: string;
  precioMin?: number;
  precioMax?: number;
  stockMin?: number;
  stockMax?: number;
};

const basePath = "/api/productos";

function buildSearchQuery(params: SearchParams): string {
  const query = new URLSearchParams();
  if (params.nombre) query.set("nombre", params.nombre);
  if (params.codigo) query.set("codigo", params.codigo);
  if (params.precioMin != null) query.set("precioMin", String(params.precioMin));
  if (params.precioMax != null) query.set("precioMax", String(params.precioMax));
  if (params.stockMin != null) query.set("stockMin", String(params.stockMin));
  if (params.stockMax != null) query.set("stockMax", String(params.stockMax));
  const qs = query.toString();
  return qs ? `?${qs}` : "";
}

export const productoService = {
  getAll: () => http.get<Producto[]>(basePath),
  getById: (id: number) => http.get<Producto>(`${basePath}/${id}`),
  create: (producto: Partial<Producto>) => http.post<Producto>(basePath, producto),
  update: (id: number, producto: Partial<Producto>) =>
    http.put<Producto>(`${basePath}/${id}`, producto),
  delete: (id: number) => http.delete<void>(`${basePath}/${id}`),
  search: (params: SearchParams) =>
    http.get<Producto[]>(`${basePath}/search${buildSearchQuery(params)}`),
  getCategorias: () => http.get<string[]>("/api/categorias"),
  aplicarDescuento: (id: number, descuentoPromocional: number) =>
    http.put<Producto>(`${basePath}/${id}/descuento`, { descuentoPromocional }),
};
