// ==============================================================
// Página /productos (Server Component)
// --------------------------------------------------------------
// Mismo patrón que /obras: el server solo monta al Client Component
// <ProductosPageClient> con la lista inicial vacía; el cliente hace
// el fetch con reintentos y aplica los filtros de búsqueda.
// ==============================================================

import ProductosPageClient from './ProductosPageClient';

/**
 * Página /productos: Server Component que monta el cliente interactivo.
 *
 * @returns {JSX.Element} El <ProductosPageClient> con la lista inicial vacía.
 */
export default function ProductosPage () {
  return <ProductosPageClient initialProductos={[]} />;
}
