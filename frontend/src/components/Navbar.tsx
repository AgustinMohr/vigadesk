// ==============================================================
// NAVBAR — barra superior: marca + usuario + botón "Salir"
// --------------------------------------------------------------
// Client Component: muestra el nombre del usuario logueado (useAuth)
// y su rol en una "pill" (etiqueta). El botón "Salir" no navega:
// llama a logout() (borra la sesión) y el AuthGuard redirige solo
// a /login. Se usa preventDefault porque el Link apunta a /login.
// ==============================================================

'use client';

import Link from 'next/link';
import { useAuth } from '@/lib/auth/AuthContext';

export default function Navbar () {
  const { user, logout } = useAuth();

  return (
    <nav className='flex h-16 items-center justify-between border-b border-slate-200 bg-white px-6'>
      <Link href='/dashboard' className='text-lg font-semibold text-slate-950'>
        Mi Sistema
      </Link>

      <div className='flex items-center gap-4'>
        <span className='text-sm text-slate-500'>
          {/* Nombre del usuario + etiqueta según rol. Para rol CLIENTE
              muestra su empresa (clienteNombre). */}
          {user?.nombre}{' '}
          <span className='ml-1 rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600'>
            {user?.rol === 'ADMIN'
              ? 'Admin'
              : user?.rol === 'CLIENTE'
                ? user.clienteNombre ?? 'Cliente'
                : 'Invitado'}
          </span>
        </span>
        <Link
          href='/login'
          onClick={(e) => {
            e.preventDefault(); // no navegamos por el Link; cerramos sesión
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
