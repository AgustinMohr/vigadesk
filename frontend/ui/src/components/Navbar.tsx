// ==============================================================
// NAVBAR — barra superior: marca + usuario + botón "Salir"
// --------------------------------------------------------------
// Client Component ('use client'): muestra el nombre del usuario
// logueado (useAuth) y su rol en una "pill" (etiqueta). El botón
// "Salir" no navega por el Link: llama a logout() (borra la sesión
// del localStorage + contexto) y el AuthLayout/guard redirige solo.
//
// FLUJO DEL LOGOUT:
//  1. Click en "Salir" → `e.preventDefault()` anula la navegación del Link.
//  2. Se ejecuta `logout()` → AuthContext limpia auth_token/auth_user y
//     pone `user` en null.
//  3. El re-render hace que el AuthGuard vea `user === null` y redireccione
//     al /login (React Router de Next: router.replace, ver AuthLayout).
//
// CONCEPTOS CLAVE: Client Component (interacción + hooks), render
// condicional ternario, optional chaining en `user?.rol`.
// ==============================================================

'use client';

import Link from 'next/link';
import { useAuth } from '@/lib/auth/AuthContext';

/**
 * Barra superior de la aplicación (layout del área autenticada).
 *
 * No recibe props: obtiene el estado de sesión del Context global con
 * useAuth(). Al loguearse/desloguearse, el context cambia y este
 * componente se re-renderiza automáticamente (mismo patrón en Sidebar).
 *
 * @returns {JSX.Element} La barra con marca, nombre del usuario y Salir.
 */
export default function Navbar () {
  // Desestructuración del objeto del context: `user` (o null) y `logout`.
  const { user, logout } = useAuth();

  return (
    <nav className='flex h-16 items-center justify-between border-b border-slate-200 bg-white px-6'>
      {/* next/link → navegación client-side sin recargar la página completa */}
      <Link href='/dashboard' className='text-lg font-semibold text-slate-950'>
        Mi Sistema
      </Link>

      <div className='flex items-center gap-4'>
        <span className='text-sm text-slate-500'>
          {/* Nombre del usuario + etiqueta según rol. Ternarios anidados:
              si es ADMIN muestra "Admin"; si es CLIENTE muestra su empresa
              (clienteNombre) o "Cliente"; cualquier otro rol, "Invitado".
              `user?.nombre` con optional chaining: si user es null,
              devuelve undefined (no lanza error). */}
          {user?.nombre}{' '}
          <span className='ml-1 rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600'>
            {user?.rol === 'ADMIN'
              ? 'Admin'
              : user?.rol === 'CLIENTE'
                ? user.clienteNombre ?? 'Cliente' // ?? (nullish): usa clienteNombre si existe, si no "Cliente"
                : 'Invitado'}
          </span>
        </span>

        <Link
          href='/login'
          onClick={(e) => {
            // `preventDefault()` cancela el comportamiento por defecto del
            // Link (navegar a /login). Acá el Link es SOLO la apariencia
            // de botón; la acción real es logout().
            e.preventDefault();
            logout();
          }}
          className='rounded-md border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100'
        >
          Salir
        </Link>
      </div>
    </nav>
  );
}
