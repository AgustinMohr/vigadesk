// Servicio API para el CRUD de clientes
import { http } from "./http";

export type Cliente = {
  id?: number;
  nombre: string;
  correoElectronico: string;
  cuit?: string;
  maximoDescubierto?: number;
  maxObrasActivas?: number;
};

const basePath = "/api/clientes";

export const clienteService = {
  getAll: () => http.get<Cliente[]>(basePath),
  getById: (id: number) => http.get<Cliente>(`${basePath}/${id}`),
  create: (cliente: Partial<Cliente>) => http.post<Cliente>(basePath, cliente),
  update: (id: number, cliente: Partial<Cliente>) =>
    http.put<Cliente>(`${basePath}/${id}`, cliente),
  delete: (id: number) => http.delete<void>(`${basePath}/${id}`),
};
