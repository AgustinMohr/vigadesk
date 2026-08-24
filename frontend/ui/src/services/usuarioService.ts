// ==============================================================
// SERVICE DE USUARIOS DE CLIENTE — la capa que habla con la API
// --------------------------------------------------------------
// Mismo patrón de arquitectura que obraService.ts (ver ahí la teoría
// completa del patrón de Services). Particularidad: además del CRUD
// de usuarios, expone login() que autentica contra ms-clientes
// (POST /api/usuarios/login). Lo usa AuthContext para loguear usuarios
// de tipo CLIENTE contra el backend real.
//
// NOTA DE SEGURIDAD: `password` es OPCIONAL porque el backend NUNCA lo
// expone en las respuestas (se tipa para el formulario, pero al leer
// un usuario desde el GET no llegará). El password viaja a la API solo
// en el body del login() o del create()/update().
// ==============================================================

import { http } from './http';

/** Referencia mínima del cliente (empresa) a la que pertenece el usuario. */
export type ClienteRef = {
  id: number;
  nombre: string;
};

/**
 * Usuario de cliente (empleado de una empresa). El `id` es numérico
 * (vive en ms-clientes, base SQL). `habilitado` / `fechaBorrado` son para
 * soft-delete: borrar un usuario lo marca con fechaBorrado en vez de
 * eliminarlo físicamente (auditoría).
 */
export type UsuarioCliente = {
  id?: number;
  nombre: string;
  apellido?: string;
  dni?: string;
  email: string;
  password?: string; // no se expone en las respuestas del backend
  cliente: ClienteRef;
  habilitado?: boolean;
  fechaBorrado?: string;
};

/**
 * Respuesta del login (POST /api/usuarios/login): el token firmado + los
 * datos del usuario que el frontend necesita para armar la sesión
 * (guardarlos en localStorage y mostrar el nombre/rol en la UI).
 *
 * @typedef {Object} LoginResponse
 * @property {string} token - JWT firmado. Se guarda como "auth_token" y se
 *   reenvía en cada request con "Authorization: Bearer <token>".
 * @property {string} email - Email usado para el login.
 * @property {string} nombre - Nombre visible (Navbar).
 * @property {string} rol - Rol como string ("ADMIN" | "GUEST" | "CLIENTE").
 * @property {number} [clienteId] - Solo para CLIENTE: id de su empresa.
 * @property {string} [clienteNombre] - Solo para CLIENTE: nombre de su empresa.
 */
export type LoginResponse = {
  token: string;
  email: string;
  nombre: string;
  rol: string;
  clienteId?: number;
  clienteNombre?: string;
};

// Prefijo común de todas las rutas de usuarios.
const basePath = '/api/usuarios';

/**
 * Objeto con las operaciones del CRUD de usuarios de cliente + login.
 */
export const usuarioService = {
  // GET /api/usuarios → lista completa de usuarios de clientes
  getAll: () => http.get<UsuarioCliente[]>(basePath),

  // POST /api/usuarios → crea un usuario (create siempre con Partial)
  create: (usuario: Partial<UsuarioCliente>) =>
    http.post<UsuarioCliente>(basePath, usuario),

  // PUT /api/usuarios/:id → actualiza el usuario existente
  update: (id: number, usuario: Partial<UsuarioCliente>) =>
    http.put<UsuarioCliente>(`${basePath}/${id}`, usuario),

  // DELETE /api/usuarios/:id → borrado lógico; HTTP 204
  delete: (id: number) => http.delete<void>(`${basePath}/${id}`),

  // POST /api/usuarios/login → autentica con email+password y devuelve
  // el JWT. Usa `http.post` (no get) porque envía credenciales en el body.
  login: (email: string, password: string) =>
    http.post<LoginResponse>(`${basePath}/login`, { email, password }),
};
