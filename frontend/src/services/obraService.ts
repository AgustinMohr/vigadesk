// Servicio API para obras y asignación cliente-obra
import { http } from "./http";

export type EstadoObra = "HABILITADA" | "PENDIENTE" | "FINALIZADA";

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

const basePath = "/api/obras";

export const obraService = {
  getAll: () => http.get<Obra[]>(basePath),
  getById: (id: number) => http.get<Obra>(`${basePath}/${id}`),
  getByClienteId: (clienteId: number) =>
    http.get<Obra[]>(`${basePath}/by-cliente/${clienteId}`),
  getSinAsignar: () => http.get<Obra[]>(`${basePath}/sin-asignar`),
  asignarCliente: (obraId: number, clienteId: number) =>
    http.put<Obra>(`${basePath}/${obraId}/asignar-cliente/${clienteId}`, {}),
  cambiarEstado: (obraId: number, estado: EstadoObra) =>
    http.put<Obra>(`${basePath}/${obraId}/estado`, { estado }),
};
