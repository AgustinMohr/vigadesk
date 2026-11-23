'use client';

import { useEffect } from 'react';

/**
 * Último recurso: un error que rompe el layout raíz. Reemplaza al layout, por eso trae su propio <html> y
 * <body> y no usa los estilos globales ni los componentes compartidos.
 */
export default function ErrorGlobal ({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <html lang='es'>
      <body style={{ fontFamily: 'system-ui, sans-serif', display: 'flex', minHeight: '100vh', alignItems: 'center', justifyContent: 'center', background: '#f8fafc' }}>
        <main role='alert' style={{ maxWidth: 420, padding: 32, textAlign: 'center', background: 'white', border: '1px solid #e2e8f0', borderRadius: 8 }}>
          <h1 style={{ fontSize: 20, margin: 0 }}>Algo salió mal</h1>
          <p style={{ color: '#475569', fontSize: 14 }}>La aplicación tuvo un error inesperado.</p>
          <button type='button' onClick={reset} style={{ padding: '8px 12px', border: '1px solid #cbd5e1', borderRadius: 4, background: 'white', cursor: 'pointer' }}>
            Reintentar
          </button>
        </main>
      </body>
    </html>
  );
}
