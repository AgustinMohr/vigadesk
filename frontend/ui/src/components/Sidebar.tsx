// ==============================================================
// SIDEBAR — menú lateral de navegación
// --------------------------------------------------------------
// Client Component porque necesita el usuario logueado (useAuth) para
// decidir qué opciones mostrar según el rol.
//
// NAVEGACIÓN CON <Link> (next/link): a diferencia de un <a> normal,
// Link navega por CLIENTE (client-side), sin recargar toda la página.
// Next.js solo re-renderiza el segmento que cambia (los layouts comunes
// se mantienen). Es más rápido y mantiene el estado de la app.
//
// CONTROL DE ACCESO POR ROL:
//  - Admin: ve todo.
//  - CLIENTE: solo lo operativo (productos y pedidos), sin las pantallas
//    adminOnly (no administra clientes/obras/usuarios).
//  - Invitado (GUEST): solo catálogo de productos; sin datos de clientes,
//    obras, pedidos ni pagos.
// ==============================================================

'use client';

import Link from 'next/link';
import { useAuth } from '@/lib/auth/AuthContext';
import { RUTAS } from '@/lib/auth/access';

// Centralizamos las opciones del menú en un array. Para agregar una
// pantalla nueva alcanza con sumar otro objeto acá. `adminOnly: true`
// marca las pantallas que solo ve el admin.

/**
 * Menú lateral fijo de la aplicación.
 *
 * Recibe el rol desde el Context global y FILTRA la tabla de rutas
 * (`RUTAS`) antes de renderizar. Este filtro es solo de UX; la seguridad
 * REAL la aplica el backend al validar el JWT en cada request.
 *
 * @returns {JSX.Element} El <aside> con la lista de links filtrados.
 */
export default function Sidebar () {
  const { user } = useAuth(); // lee la sesión del Context global

  // Filtro por rol:
  //  - Admin: ve todo.
  //  - CLIENTE: ve solo lo operativo (productos y pedidos), sin las
  //    pantallas adminOnly (no administra clientes/obras/usuarios).
  //  - Invitado (GUEST): solo el catálogo de productos.
  // `filter` es un método de array que devuelve un NUEVO array con los
  // items que cumplen la condición (no muta `allLinks` original).
  // Solo las pantallas que el rol puede abrir (misma tabla que usa el guard de rutas)
  const links = RUTAS.filter((ruta) => user !== null && ruta.roles.includes(user.rol));

  return (
    <aside className='w-64 border-r border-slate-200 bg-white px-4 py-6'>
      <ul className='space-y-1'>
        {/* Render de lista con `.map()`: convierte CADA elemento del array
            en un <li> con su <Link>. `key` es obligatoria (identidad del
            item para que React difiere eficientemente los cambios). */}
        {links.map((link) => (
          <li key={link.href}>
            <Link
              href={link.href}
              className='block rounded-md px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100 hover:text-slate-950'
            >
              {link.label}
            </Link>
          </li>
        ))}
      </ul>
    </aside>
  );
}
