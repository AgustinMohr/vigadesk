// ==============================================================
// SERVICE DE PRODUCTOS — la capa que habla con la API
// --------------------------------------------------------------
// Mismo patrón de arquitectura que obraService.ts (ver ahí la teoría
// completa del patrón de Services). Particularidades de productos:
//  - getCategorias() para poblar el dropdown del formulario.
//  - aplicarDescuento() para promociones (baja el precio del producto).
//  - provision() para REPONER stock de un producto existente (el backend
//    suma la cantidad y deja el precio intacto si no se manda).
//
// NOTA: las rutas son RELATIVAS → pasan por el proxy de Next.js que las
// traduce al microservicio "productos".
// ==============================================================

import { http } from './http';

/**
 * Forma de un producto según el JSON del backend.
 * Los campos `?` son opcionales; `precio` y `stockActual`/`stockMinimo`
 * son obligatorios porque el formulario los requiere y el negocio los usa
 * (el módulo StockBajo avisa cuando stockActual < stockMinimo).
 *
 * @typedef {Object} Producto
 * @property {number} [id] - Identificador numérico del producto.
 * @property {string} [codigo] - Código interno del producto (SKU, ej: "P-001").
 * @property {string} nombre - Nombre visible del producto (obligatorio).
 * @property {string} [descripcion] - Detalle/descripción opcional.
 * @property {number} precio - Precio de venta unitario (obligatorio).
 * @property {number} stockActual - Cantidad disponible hoy (admite decimales).
 * @property {number} stockMinimo - Umbral bajo el cual se alerta por stock bajo.
 * @property {string} [unidadMedida] - UNIDAD, BOLSA, M3, KG, METRO, LITRO o TONELADA.
 * @property {number} [costo] - Costo unitario para calcular margen.
 * @property {number} [descuentoPromocional] - % de promoción activa (0-100).
 * @property {string} [categoria] - Categoría textual (ej: "Materiales").
 */
export type Producto = {
  id?: number;
  codigo?: string;
  nombre: string;
  descripcion?: string;
  precio: number;
  stockActual: number;
  stockMinimo: number;
  unidadMedida?: string;
  costo?: number;
  descuentoPromocional?: number;
  categoria?: string;
};

// Prefijo común de todas las rutas de productos.
const basePath = '/api/productos';

/**
 * Objeto con las operaciones del CRUD + promociones de productos.
 */
export const productoService = {
  // GET /api/productos → lista completa
  getAll: () => http.getAll<Producto>(basePath),

  // POST /api/productos → crea un producto (create siempre con Partial)
  create: (producto: Partial<Producto>) => http.post<Producto>(basePath, producto),

  // PUT /api/productos/:id → actualiza el producto existente
  update: (id: number, producto: Partial<Producto>) =>
    http.put<Producto>(`${basePath}/${id}`, producto),

  // DELETE /api/productos/:id → elimina; HTTP 204 (sin body → <void>)
  delete: (id: number) => http.delete<void>(`${basePath}/${id}`),

  // GET /api/categorias → lista de categorías para el dropdown del form
  getCategorias: () => http.get<string[]>('/api/categorias'),

  // PUT /api/productos/:id/descuento → aplica el % promocional al producto
  aplicarDescuento: (id: number, descuentoPromocional: number) =>
    http.put<Producto>(`${basePath}/${id}/descuento`, { descuentoPromocional }),

  // PUT /api/productos/provision → repone stock: suma `cantidad` al stock
  // actual del producto. El precio NO se toca (se edita con el formulario).
  provision: (id: number, cantidad: number) =>
    http.put<Producto>(`${basePath}/provision`, { idProducto: id, cantidad }),
};
