// ==============================================================
// PÁGINA DE LOGIN (Client Component)
// --------------------------------------------------------------
// A diferencia de los Server Components, acá necesitamos interactividad
// (estado, eventos del form), por eso lleva "use client".
// OJO: esta página tiene SU PROPIO <AuthProvider> (no está bajo el
// layout (app)), porque el login vive fuera de las páginas protegidas.
// El provider se coloca arriba para que <LoginForm> pueda usar useAuth().
// ==============================================================

'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { AuthProvider, useAuth } from '@/lib/auth/AuthContext';

function LoginForm () {
  const { login } = useAuth(); // función async de autenticación (AuthContext.tsx)
  const router = useRouter(); // navegación programática de Next
  const [error, setError] = useState(''); // mensaje de error en el form

  // --------------------------------------------------------------
  // useState: estado local del componente.
  // setError("") limpia el mensaje; setError("Email o contraseña...")
  // lo muestra. Cada vez que cambia el estado, React re-renderiza
  // el componente para reflejarlo en pantalla.
  // --------------------------------------------------------------

  // Manejador del evento "submit" del formulario (se dispara al presionar Entrar).
  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault(); // evita que el navegador recargue la página al enviar
    setError('');

    // FormData lee los <input> del form por su atributo `name`.
    const form = new FormData(e.currentTarget);
    const email = form.get('email') as string;
    const password = form.get('password') as string;

    // `await` pausa esta función hasta que login() termine (puede ir
    // al backend real). Devuelve true (login ok) o false (credenciales malas).
    const ok = await login(email, password);
    if (!ok) {
      setError('Email o contraseña incorrectos.');
      return;
    }

    // Navega a /dashboard reemplazando el historial (no se puede "volver"
    // con el botón atrás al login).
    router.replace('/dashboard');
  };

  return (
    <main className='flex min-h-screen items-center justify-center bg-slate-50 px-4'>
      <section className='w-full max-w-sm rounded-lg border border-slate-200 bg-white p-6 shadow-sm'>
        <h1 className='text-2xl font-semibold text-slate-950'>Iniciar sesión</h1>
        <p className='mt-2 text-sm text-slate-600'>
          Ingresa tus datos para acceder al sistema.
        </p>

        <form onSubmit={handleSubmit} className='mt-6 space-y-4'>
          <div>
            <label htmlFor='email' className='block text-sm font-medium text-slate-700'>
              Email
            </label>
            {/* `name` conecta el input con FormData (form.get("email")) */}
            <input
              id='email'
              name='email'
              type='email'
              required
              className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 outline-none focus:border-slate-500'
              placeholder='usuario@email.com'
            />
          </div>

          <div>
            <label htmlFor='password' className='block text-sm font-medium text-slate-700'>
              Contraseña
            </label>
            <input
              id='password'
              name='password'
              type='password'
              required
              className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 outline-none focus:border-slate-500'
              placeholder='********'
            />
          </div>

          {/* Render condicional: si `error` tiene texto, se muestra en rojo */}
          {error && (
            <p className='text-sm text-red-600'>{error}</p>
          )}

          <button
            type='submit' // type="submit" hace que active el onSubmit del form
            className='w-full rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800'
          >
            Entrar
          </button>
        </form>
      </section>
    </main>
  );
}

// Exporta la página envolviendo el form con el AuthProvider.
export default function LoginPage () {
  return (
    <AuthProvider>
      <LoginForm />
    </AuthProvider>
  );
}
