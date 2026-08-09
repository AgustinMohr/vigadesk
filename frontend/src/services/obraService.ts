// ==============================================================
// SERVICE DE OBRAS — la capa que habla con la API
// --------------------------------------------------------------
// PATRÓN DE LOS SERVICES: cada entidad del backend (obras, clientes,
// pedidos...) tiene su archivo acá. Su trabajo es:
//  1. Definir el TIPO TypeScript de la entidad (cómo se ve en el JSON).
//  2. Exponer funciones tipadas que llaman a http.get/post/put/delete
//     contra la ruta correcta. El componente usa estas funciones en vez
//     de llamar fetch directamente → el resto de la app no sabe "dónde"
//     vive la API (está escondido acá).
// Los tipos se exportan para que los componentes los importen con
// `import { obraService, type Obra } from "@/services/obraService"`.
// ==============================================================

import { http } from './http';

// Estados posibles de una obra (coinciden con el backend).
export type EstadoObra = 'HABILITADA' | 'PENDIENTE' | 'FINALIZADA';

// Forma de una obra según el JSON que devuelve la API.
// Los campos con `?` son opcionales; `cliente` puede ser null
// (obra sin cliente asignado).
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
// relativa → el proxy (app/api/[...path]/route.ts) la redirige al gateway.
const basePath = '/api/obras';

// Objeto con las operaciones del CRUD. Cada método mapea 1 a 1 a un
// endpoint REST del backend y tipa la respuesta con <T>.
export const obraService = {
  getAll: () => http.get<Obra[]>(basePath),
  getById: (id: number) => http.get<Obra>(`${basePath}/${id}`),
  getByClienteId: (clienteId: number) =>
    http.get<Obra[]>(`${basePath}/by-cliente/${clienteId}`),
  getSinAsignar: () => http.get<Obra[]>(`${basePath}/sin-asignar`),
  create: (obra: Partial<Obra>) => http.post<Obra>(basePath, obra),
  update: (id: number, obra: Partial<Obra>) =>
    http.put<Obra>(`${basePath}/${id}`, obra),
  delete: (id: number) => http.delete<void>(`${basePath}/${id}`),
  asignarCliente: (obraId: number, clienteId: number) =>
    http.put<Obra>(`${basePath}/${obraId}/asignar-cliente/${clienteId}`, {}),
  cambiarEstado: (obraId: number, estado: EstadoObra) =>
    http.put<Obra>(`${basePath}/${obraId}/estado`, { estado }),
};
