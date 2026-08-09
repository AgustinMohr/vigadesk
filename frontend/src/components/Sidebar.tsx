// ==============================================================
// SIDEBAR — menú lateral de navegación
// --------------------------------------------------------------
// Client Component porque necesita el usuario logueado (useAuth) para
// decidir qué opciones mostrar.
//
// NAVEGACIÓN CON <Link> (next/link): a diferencia de un <a> normal,
// Link navega por CLIENTE, sin recargar toda la página (el layout común
// se mantiene y solo cambia la página). Es más rápido.
// ==============================================================

'use client';

import Link from 'next/link';
import { useAuth } from '@/lib/auth/AuthContext';

// Centralizamos las opciones del menú en un array. Para agregar una
// pantalla nueva alcanza con sumar otro objeto acá. `adminOnly: true`
// marca las pantallas que solo ve el admin.
const allLinks = [
  { href: '/dashboard', label: 'Dashboard' },
  { href: '/clientes', label: 'Clientes', adminOnly: true },
  { href: '/obras', label: 'Obras', adminOnly: true },
  { href: '/usuarios', label: 'Usuarios', adminOnly: true },
  { href: '/productos', label: 'Productos' },
  { href: '/pedidos', label: 'Pedidos' },
];

export default function Sidebar () {
  const { user, isAdmin } = useAuth(); // lee la sesión del Context global

  // Filtro por rol:
  //  - Admin: ve todo.
  //  - CLIENTE: ve solo lo operativo (productos y pedidos), sin las
  //    pantallas adminOnly (no administra clientes/obras/usuarios).
  //  - Invitado (GUEST): mantiene el acceso completo.
  const links = allLinks.filter((link) => {
    if (isAdmin) return true;
    if (user?.rol === 'CLIENTE') return !link.adminOnly;
    return true;
  });

  return (
    <aside className='w-64 border-r border-slate-200 bg-white px-4 py-6'>
      <ul className='space-y-1'>
        {/* Render de lista: un <li> con su <Link> por cada opción */}
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
