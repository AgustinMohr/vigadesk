// ==============================================================
// SERVICE DE USUARIOS DE CLIENTE (mismo patrón que obraService.ts)
// Particularidad: además del CRUD, expone login() que autentica contra
// ms-clientes (POST /api/usuarios/login). Lo usa AuthContext para
// loguear usuarios de tipo CLIENTE contra el backend real.
// ==============================================================
import { http } from './http';

// Referencia mínima del cliente (empresa) a la que pertenece el usuario
export type ClienteRef = {
  id: number;
  nombre: string;
};

export type UsuarioCliente = {
  id?: number;
  nombre: string;
  email: string;
  password?: string; // no se expone en las respuestas del backend
  cliente: ClienteRef;
  habilitado?: boolean;
  fechaBorrado?: string;
};

// Respuesta del login (POST /api/usuarios/login): el token firmado + los
// datos del usuario que el frontend necesita para armar la sesión.
export type LoginResponse = {
  token: string;
  email: string;
  nombre: string;
  rol: string;
  clienteId?: number;
  clienteNombre?: string;
};

const basePath = '/api/usuarios';

export const usuarioService = {
  getAll: () => http.get<UsuarioCliente[]>(basePath),
  getById: (id: number) => http.get<UsuarioCliente>(`${basePath}/${id}`),
  create: (usuario: Partial<UsuarioCliente>) =>
    http.post<UsuarioCliente>(basePath, usuario),
  update: (id: number, usuario: Partial<UsuarioCliente>) =>
    http.put<UsuarioCliente>(`${basePath}/${id}`, usuario),
  delete: (id: number) => http.delete<void>(`${basePath}/${id}`),
  login: (email: string, password: string) =>
    http.post<LoginResponse>(`${basePath}/login`, { email, password }),
};
