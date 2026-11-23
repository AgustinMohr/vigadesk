'use client';

import { useEffect } from 'react';

/**
 * Error de renderizado dentro del área autenticada: en lugar de una pantalla en blanco muestra un mensaje y
 * permite reintentar (Next vuelve a renderizar el segmento) sin perder la barra ni el menú.
 */
export default function ErrorDelArea ({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    console.error(error); // queda en la consola del navegador para quien soporta el sistema
  }, [error]);

  return (
    <section role='alert' className='mx-auto max-w-md rounded-lg border border-red-200 bg-white p-8 text-center'>
      <h2 className='text-xl font-semibold text-slate-900'>Algo salió mal</h2>
      <p className='mt-2 text-sm text-slate-600'>Ocurrió un error al mostrar esta pantalla. Podés reintentar.</p>
      <button
        type='button'
        onClick={reset}
        className='mt-4 rounded border border-slate-300 px-3 py-2 text-sm text-slate-700 hover:bg-slate-100'
      >
        Reintentar
      </button>
    </section>
  );
}
