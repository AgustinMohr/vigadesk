/**
 * Error de carga de una pantalla, con el botón "Reintentar" cuando volver a pedir puede servir.
 * Sin `onRetry` (por ejemplo, un 403) solo muestra el mensaje.
 */
export default function FetchError ({ mensaje, onRetry }: { mensaje: string; onRetry?: () => void }) {
  return (
    <div role='alert' className='flex items-center gap-3 rounded border border-red-300 bg-red-50 px-4 py-3 text-red-700'>
      <p className='flex-1'>{mensaje}</p>
      {onRetry && (
        <button
          type='button'
          onClick={onRetry}
          className='rounded border border-red-300 bg-white px-3 py-1 text-sm hover:bg-red-100 transition'
        >
          Reintentar
        </button>
      )}
    </div>
  );
}
