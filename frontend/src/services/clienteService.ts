// ==============================================================
// SERVICE DE CLIENTES (mismo patrón que obraService.ts)
// Solo CRUD: la app no tiene búsqueda especial de clientes.
// ==============================================================
import { http } from './http';

// Forma de un cliente según el JSON del backend
export type Cliente = {
  id?: number;
  nombre: string;
  correoElectronico: string;
  cuit?: string;
  maximoDescubierto?: number;
  maxObrasActivas?: number;
};

const basePath = '/api/clientes';

export const clienteService = {
  getAll: () => http.get<Cliente[]>(basePath),
  getById: (id: number) => http.get<Cliente>(`${basePath}/${id}`),
  create: (cliente: Partial<Cliente>) => http.post<Cliente>(basePath, cliente),
  update: (id: number, cliente: Partial<Cliente>) =>
    http.put<Cliente>(`${basePath}/${id}`, cliente),
  delete: (id: number) => http.delete<void>(`${basePath}/${id}`),
};
