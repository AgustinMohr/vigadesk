// ==============================================================
// Página /obras (Server Component)
// --------------------------------------------------------------
// Este `page.tsx` es un Server Component (sin "use client"): se renderiza
// en el servidor. Su único trabajo es decirle al CLIENT Component
// <ObrasPageClient> con qué datos empezar (initialObras).
//
// PATRÓN CLÁVE de Next.js: los componentes de servidor pueden pasar
// props "hacia abajo" a componentes de cliente. Acá le pasamos un array
// vacío porque el SSR no consulta la API (evita duplicar llamadas);
// el cliente se encarga de traer los datos.
// ==============================================================

import ObrasPageClient from './ObrasPageClient';

/**
 * Página /obras: Server Component que monta el cliente interactivo.
 *
 * @returns {JSX.Element} El <ObrasPageClient> con la lista inicial vacía.
 */
export default function ObrasPage () {
  return <ObrasPageClient />;
}
