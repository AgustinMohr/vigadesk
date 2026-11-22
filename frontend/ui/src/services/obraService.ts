// ==============================================================
// SERVICE DE OBRAS — la capa que habla con la API
// --------------------------------------------------------------
// PATRÓN DE LOS SERVICES: cada entidad del backend (obras, clientes,
// pedidos...) tiene su archivo acá. Su trabajo es:
//  1. Definir el TIPO TypeScript de la entidad (cómo se ve el JSON).
//  2. Exponer funciones tipadas que llaman a http.get/post/put/delete
//     contra la ruta correcta. El componente usa estas funciones en vez
//     de llamar fetch directamente → el resto de la app no sabe "dónde"
//     vive la API (está escondido acá).
// Los tipos se exportan para que los componentes los importen con
// `import { obraService, type Obra } from "@/services/obraService"`.
//
// NOTA DE ARQUITECTURA: las rutas son RELATIVAS ("/api/obras") → pasan por
// el proxy de Next.js (app/api/[...path]/route.ts) que las traduce al
// microservicio "clientes" (obras y usuarios viven bajo ms-clientes).
// ==============================================================

import { http } from './http';

// Estados posibles de una obra (coinciden con el backend).
// Union Type: solo estas 3 cadenas son válidas, el compilador valida.
export type EstadoObra = 'HABILITADA' | 'PENDIENTE' | 'FINALIZADA';

/**
 * Forma de una obra según el JSON que devuelve la API.
 * Los campos con `?` son opcionales; `cliente` puede ser null
 * (obra sin cliente asignado). `esRemodelacion` (boolean) distingue
 * una obra nueva de una remodelación de una existente.
 *
 * @typedef {Object} Obra
 * @property {number} [id] - Identificador numérico (autogenerado).
 * @property {string} direccion - Ubicación de la obra (obligatoria).
 * @property {boolean} esRemodelacion - True si es remodelación de obra previa.
 * @property {number} [lat] - Latitud geográfica (para mapa).
 * @property {number} [lng] - Longitud geográfica.
 * @property {number} presupuesto - Monto presupuestado para la obra.
 * @property {EstadoObra} [estado] - Ciclo de vida: habilitada → ... → finalizada.
 * @property {{id:number, nombre:string} | null} [cliente] - Cliente asignado
 *   o null si la obra está "sin asignar".
 */
export type Obra = {
  id?: number;
  direccion: string;
  esRemodelacion: boolean;
  lat?: number;
  lng?: number;
  presupuesto: number;
  estado?: EstadoObra;
  cliente?: {
    id: number;
    nombre: string;
  } | null;
};

// Prefijo común de todas las rutas de obras. La URL final siempre es
// relativa → el proxy la redirige al gateway.
const basePath = '/api/obras';

/**
 * Objeto con las operaciones del CRUD + consultas especiales de obras.
 * Cada método mapea 1 a 1 a un endpoint REST del backend y tipa la
 * respuesta con el genérico <T>.
 */
export const obraService = {
  // GET /api/obras → lista completa de obras
  getAll: () => http.getAll<Obra>(basePath),

  // GET /api/obras/by-cliente/:clienteId → obras de un cliente específico
  // (lo usa el pedido para ofrecer solo las obras activas del cliente).
  getByClienteId: (clienteId: number) =>
    http.get<Obra[]>(`${basePath}/by-cliente/${clienteId}`),

  // GET /api/obras/sin-asignar → obras sin cliente (para asignarlas luego)
  getSinAsignar: () => http.get<Obra[]>(`${basePath}/sin-asignar`),

  // POST /api/obras → crea una obra (create siempre con Partial)
  create: (obra: Partial<Obra>) => http.post<Obra>(basePath, obra),

  // PUT /api/obras/:id → actualiza los datos generales de la obra
  update: (id: number, obra: Partial<Obra>) =>
    http.put<Obra>(`${basePath}/${id}`, obra),

  // DELETE /api/obras/:id → elimina; HTTP 204 (sin body → <void>)
  delete: (id: number) => http.delete<void>(`${basePath}/${id}`),

  // PUT /api/obras/:id/asignar-cliente/:clienteId → vincula la obra a un cliente.
  // El body {} (objeto vacío) porque el recurso viaja en la URL, no en el body.
  asignarCliente: (obraId: number, clienteId: number) =>
    http.put<Obra>(`${basePath}/${obraId}/asignar-cliente/${clienteId}`, {}),

  // PATCH /api/obras/:id/estado → cambia el ciclo de vida (ej: a FINALIZADA).
  cambiarEstado: (obraId: number, estado: EstadoObra) =>
    http.patch<Obra>(`${basePath}/${obraId}/estado`, { estado }),
};
