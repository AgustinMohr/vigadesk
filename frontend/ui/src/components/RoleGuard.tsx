'use client';

import Link from 'next/link';
import type { ReactNode } from 'react';
import type { UserRole } from '@/lib/auth/types';

/** Indicador de carga accesible (en lugar de dejar la pantalla en blanco). */
export function Cargando ({ texto = 'Cargando…' }: { texto?: string }) {
  return (
    <div role='status' className='flex min-h-[40vh] items-center justify-center gap-3 text-slate-500'>
      <span aria-hidden='true' className='h-5 w-5 animate-spin rounded-full border-2 border-slate-300 border-t-slate-600' />
      <span>{texto}</span>
    </div>
  );
}

/** Pantalla que se muestra cuando el rol del usuario no puede abrir la ruta. */
export function SinAcceso () {
  return (
    <section role='alert' className='mx-auto max-w-md rounded-lg border border-slate-200 bg-white p-8 text-center'>
      <h2 className='text-xl font-semibold text-slate-900'>Sin acceso</h2>
      <p className='mt-2 text-sm text-slate-600'>Tu usuario no tiene permiso para ver esta pantalla.</p>
      <Link href='/dashboard' className='mt-4 inline-block rounded border border-slate-300 px-3 py-2 text-sm text-slate-700 hover:bg-slate-100'>
        Volver al inicio
      </Link>
    </section>
  );
}

/**
 * Deja pasar a `children` solo si el rol está permitido. Si no, muestra "Sin acceso" y NO monta la pantalla:
 * así un usuario sin permiso no dispara ninguna llamada al backend.
 * Sin sesión todavía (`rol` indefinido) muestra el indicador de carga.
 */
export default function RoleGuard ({ rol, roles, children }: { rol: UserRole | undefined; roles: readonly UserRole[] | null; children: ReactNode }) {
  if (rol === undefined) return <Cargando />;
  if (roles !== null && !roles.includes(rol)) return <SinAcceso />;
  return <>{children}</>;
}
