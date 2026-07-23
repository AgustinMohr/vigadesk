import ProductosPageClient from "./ProductosPageClient";

// Server Component: se renderiza en el servidor.
// Pasa array vacío para que el cliente haga fetch con reintentos.
export default function ProductosPage() {
  return <ProductosPageClient initialProductos={[]} />;
}
