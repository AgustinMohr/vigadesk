// ==============================================================
// AUTHLayout — protege las páginas del grupo (app)
// --------------------------------------------------------------
// "use client" convierte a este archivo en un CLIENT Component:
// se renderiza (al menos la parte interactiva) en el navegador.
//
// Funcionamiento:
//  1. <AuthProvider> envuelve toda la app con el Context de auth, para
//     que cualquier componente pueda llamar a useAuth().
//  2. <AuthGuard> decide si mostrar el contenido o redirigir a /login.
//  3. Si hay sesión, dibuja el esqueleto común: Navbar arriba + Sidebar
//     a la izquierda + la página actual ({children}) en el centro.
// ==============================================================

'use client';

import { useEffect, useState, useSyncExternalStore } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { AuthProvider, useAuth } from '@/lib/auth/AuthContext';
import Navbar from '@/components/Navbar';
import { FeedbackProvider } from '@/components/Feedback';
import Sidebar from '@/components/Sidebar';
import RoleGuard, { Cargando } from '@/components/RoleGuard';
import { rolesDeRuta } from '@/lib/auth/access';

// useSyncExternalStore espera una función "subscribe" que devuelva un
// "unsubscribe". Como no escuchamos nada, usamos esta que no hace nada
// (hay que devolver una función, por eso `() => () => {}`).
const emptySubscribe = () => () => {};

function AuthGuard ({ children }: { children: React.ReactNode }) {
  const { user } = useAuth(); // lee el usuario del Context (ver AuthContext.tsx)
  const router = useRouter(); // hook de Next para navegar programáticamente
  const pathname = usePathname(); // ruta actual: decide qué roles pueden verla
  const [menuAbierto, setMenuAbierto] = useState(false); // menú lateral en pantallas chicas

  // ------------------------------------------------------------------
  // DETECCIÓN DE MONTAJE (por qué existe)
  // ------------------------------------------------------------------
  // Este guard decide si renderiza la app o null. El problema: durante
  // el SSR (render en servidor) el navegador NO existe, así que
  // `user` siempre es null ahí, pero en el cliente ya hay sesión en
  // localStorage. Si renderizáramos distinto en server vs client,
  // React se queja ("hydration mismatch", el error #418 que arreglamos).
  //
  // La solución: `useSyncExternalStore` devuelve false en el servidor y
  // true apenas corre en el navegador. Mientras es false, renderizamos
  // exactamente lo mismo que el server (null) → sin mismatch. Después
  // de montar, `user` ya es fiable y mostramos el contenido real.
  const isMounted = useSyncExternalStore(
    emptySubscribe,  // no suscribimos a nada
    () => true,      // valor en el cliente (navegador)
    () => false     // valor en el servidor (SSR)
  );

  // Si ya montamos y no hay usuario → redirigir al login.
  useEffect(() => {
    if (isMounted && !user) router.replace('/login');
  }, [isMounted, user, router]);

  // Mientras no estemos montados o no haya sesión, no dibujamos nada
  // (evita un flash del contenido protegido y el mismatch de hydratación).
  if (!isMounted || !user) return <Cargando />;

  // Usuario logueado → estructura común de la app
  return (
    <div className='min-h-screen bg-slate-50 text-slate-950'>
      {/* Enlace para saltar el menú con el teclado: solo se ve al enfocarlo */}
      <a href='#contenido' className='sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-50 focus:rounded focus:bg-white focus:px-3 focus:py-2 focus:text-sm focus:shadow'>
        Saltar al contenido
      </a>
      <Navbar menuAbierto={menuAbierto} onMenu={() => setMenuAbierto((abierto) => !abierto)} />
      <div className='flex min-h-[calc(100vh-64px)]'>
        <Sidebar abierto={menuAbierto} onCerrar={() => setMenuAbierto(false)} />
        {/* min-w-0: sin esto una tabla ancha estira la pagina en vez de desplazarse dentro de su caja */}
        <main id='contenido' className='min-w-0 flex-1 p-4 md:p-6'>
          <RoleGuard rol={user.rol} roles={rolesDeRuta(pathname)}>{children}</RoleGuard>
        </main>
      </div>
    </div>
  );
}

export default function AuthLayout ({ children }: { children: React.ReactNode }) {
  return (
    <AuthProvider>
      <FeedbackProvider>
        <AuthGuard>{children}</AuthGuard>
      </FeedbackProvider>
    </AuthProvider>
  );
}
