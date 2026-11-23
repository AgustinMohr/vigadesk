'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useEffect } from 'react';
import { useAuth } from '@/lib/auth/AuthContext';
import { RUTAS } from '@/lib/auth/access';

type SidebarProps = {
  /** En pantallas chicas el menú es un panel que se abre y se cierra; en las grandes está siempre visible. */
  abierto?: boolean;
  onCerrar?: () => void;
};

/**
 * Menú lateral de la aplicación. Muestra solo las pantallas que el rol puede abrir (misma tabla que el guard
 * de rutas: `RUTAS`), marca la ruta activa con `aria-current="page"` y, en pantallas chicas, se comporta como
 * un panel que se cierra con Esc, con el botón "Cerrar menú" o al elegir una opción. Este filtro es solo de
 * UX; la seguridad real la aplica el backend al validar el JWT en cada request.
 */
export default function Sidebar ({ abierto = false, onCerrar }: SidebarProps) {
  const { user } = useAuth();
  const pathname = usePathname();

  const links = RUTAS.filter((ruta) => user !== null && ruta.roles.includes(user.rol));

  useEffect(() => {
    if (!abierto || !onCerrar) return;
    const alPresionar = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCerrar();
    };
    document.addEventListener('keydown', alPresionar);
    return () => document.removeEventListener('keydown', alPresionar);
  }, [abierto, onCerrar]);

  return (
    <>
      {/* Fondo oscuro que cubre la pantalla detrás del panel móvil */}
      {abierto && <div aria-hidden='true' onClick={onCerrar} className='fixed inset-0 z-30 bg-black/40 md:hidden' />}

      <aside
        id='menu-lateral'
        className={`${abierto ? 'fixed inset-y-0 left-0 z-40 block' : 'hidden'} w-64 shrink-0 border-r border-slate-200 bg-white px-4 py-6 md:static md:block`}
      >
        <div className='mb-4 flex justify-end md:hidden'>
          <button
            type='button'
            onClick={onCerrar}
            aria-label='Cerrar menú'
            className='rounded p-1 text-2xl leading-none text-slate-500 hover:text-slate-700'
          >
            &times;
          </button>
        </div>
        <nav aria-label='Principal'>
          <ul className='space-y-1'>
            {links.map((link) => {
              const activo = pathname === link.href || pathname.startsWith(`${link.href}/`);
              return (
                <li key={link.href}>
                  <Link
                    href={link.href}
                    onClick={onCerrar}
                    aria-current={activo ? 'page' : undefined}
                    className={`block rounded-md px-3 py-2 text-sm font-medium hover:bg-slate-100 hover:text-slate-950 ${activo ? 'bg-slate-100 text-slate-950' : 'text-slate-700'}`}
                  >
                    {link.label}
                  </Link>
                </li>
              );
            })}
          </ul>
        </nav>
      </aside>
    </>
  );
}
