// ==============================================================
// TIPOS del módulo de autenticación
// --------------------------------------------------------------
// En TypeScript, un `type` define la FORMA de un dato: qué campos tiene
// y de qué tipo son. El compilador avisa si algo no respeta esa forma.
// Este archivo centraliza los tipos que comparte todo el frontend.
// ==============================================================

// Roles de usuario del sistema.
export type UserRole = 'ADMIN' | 'GUEST' | 'CLIENTE';

// Usuario logueado (lo que se guarda en localStorage y se lee con useAuth).
// El `?` marca campos OPCIONALES: clienteId/clienteNombre solo existen
// para usuarios con rol CLIENTE (su empresa).
export type User = {
  email: string;
  nombre: string;
  rol: UserRole;
  clienteId?: number;
  clienteNombre?: string;
};
