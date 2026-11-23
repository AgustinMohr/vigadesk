import type { UserRole } from './types';

/**
 * Qué roles pueden abrir cada pantalla. Es la ÚNICA fuente de verdad: la usan el menú lateral (para mostrar
 * solo lo que se puede abrir) y el guard de rutas (para no montar la pantalla, y por lo tanto no llamar al
 * backend, cuando el rol no corresponde). La seguridad real sigue estando en el backend (AccessMatrix); esto
 * evita llamadas que se sabe que van a fallar y muestra un mensaje claro.
 */
export const RUTAS: ReadonlyArray<{ href: string; label: string; roles: readonly UserRole[] }> = [
  { href: '/dashboard', label: 'Dashboard', roles: ['ADMIN', 'CLIENTE', 'GUEST'] },
  { href: '/clientes', label: 'Clientes', roles: ['ADMIN'] },
  { href: '/obras', label: 'Obras', roles: ['ADMIN'] },
  { href: '/usuarios', label: 'Usuarios', roles: ['ADMIN'] },
  { href: '/productos', label: 'Productos', roles: ['ADMIN', 'CLIENTE', 'GUEST'] },
  { href: '/pedidos', label: 'Pedidos', roles: ['ADMIN', 'CLIENTE'] },
  { href: '/importar', label: 'Importar CSV', roles: ['ADMIN'] },
];

/** Roles permitidos para una ruta, o null si la ruta no está en la tabla (no se restringe: la decide el 404). */
export function rolesDeRuta (pathname: string): readonly UserRole[] | null {
  const ruta = RUTAS.find((r) => pathname === r.href || pathname.startsWith(`${r.href}/`));
  return ruta ? ruta.roles : null;
}

export function puedeAbrir (rol: UserRole, pathname: string): boolean {
  const roles = rolesDeRuta(pathname);
  return roles === null || roles.includes(rol);
}
