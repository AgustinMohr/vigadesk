// ==============================================================
// Página /productos (Server Component)
// Mismo patrón que /obras: el server solo monta al Client Component.
// ==============================================================
import ProductosPageClient from './ProductosPageClient';

export default function ProductosPage () {
  return <ProductosPageClient initialProductos={[]} />;
}
