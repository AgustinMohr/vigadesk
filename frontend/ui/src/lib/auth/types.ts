// ==============================================================
// TIPOS del módulo de autenticación
// --------------------------------------------------------------
// En TypeScript, un `type` define la FORMA de un dato: qué campos tiene
// y de qué tipo son. El compilador avisa si algo no respeta esa forma.
// Este archivo centraliza los tipos que comparte todo el frontend:
// los roles y el usuario logueado (persistido en localStorage y leído
// desde AuthContext con el hook useAuth).
//
// CONCEPTOS CLAVE: Union Types ("ADMIN" | "GUEST" | "CLIENTE") para
// valores permitidos, y Optional Chaining (el operador `?`) en las props.
// ==============================================================

// Roles de usuario del sistema.
// Es un Union Type: el valor SOLO puede ser una de estas 3 cadenas
// literales. Si el backend devolviera "operador", TypeScript lo marcaría
// como error en tiempo de compilación (no alcanza el navegador).
export type UserRole = 'ADMIN' | 'GUEST' | 'CLIENTE';

const USER_ROLES = ['ADMIN', 'GUEST', 'CLIENTE'] as const;

/**
 * Convierte un rol recibido como string plano del backend en un UserRole
 * seguro. Si el valor no es uno de los roles conocidos, degrada a 'GUEST'
 * en vez de propagar un string inválido que rompería la UI.
 */
export function toUserRole (rol: string): UserRole {
  return (USER_ROLES as readonly string[]).includes(rol) ? rol as UserRole : 'GUEST';
}

/**
 * Representa el usuario logueado. Es la "sesión" que persiste en
 * localStorage (claves auth_token / auth_user) y que AuthContext
 * re-hidrata al iniciar la app.
 *
 * @typedef {Object} User
 * @property {string} email - Identificador único del usuario (login).
 * @property {string} nombre - Nombre visible en la interfaz (Navbar).
 * @property {UserRole} rol - Nivel de acceso; determina qué pantallas y botones
 *   se habilitan (ej: solo ADMIN crea clientes).
 * @property {number} [clienteId] - OPcional: id de la empresa solo para CLIENTE.
 * @property {string} [clienteNombre] - OPcional: nombre de la empresa del CLIENTE.
 */
export type User = {
  email: string;
  nombre: string;
  rol: UserRole;
  // El `?` marca campos OPCIONALES: clienteId/clienteNombre solo existen
  // para usuarios con rol CLIENTE (su empresa). Para ADMIN/GUEST son undefined.
  clienteId?: number;
  clienteNombre?: string;
};
