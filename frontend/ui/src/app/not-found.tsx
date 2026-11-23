import Link from 'next/link';

/** Ruta inexistente (404). */
export default function NotFound () {
  return (
    <main className='flex min-h-screen items-center justify-center bg-slate-50 p-6'>
      <section className='max-w-md rounded-lg border border-slate-200 bg-white p-8 text-center'>
        <h1 className='text-xl font-semibold text-slate-900'>Página no encontrada</h1>
        <p className='mt-2 text-sm text-slate-600'>La dirección que abriste no existe.</p>
        <Link href='/dashboard' className='mt-4 inline-block rounded border border-slate-300 px-3 py-2 text-sm text-slate-700 hover:bg-slate-100'>
          Ir al inicio
        </Link>
      </section>
    </main>
  );
}
