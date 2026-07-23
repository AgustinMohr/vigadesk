import PedidosPageClient from "./PedidosPageClient";

// Server Component: se renderiza en el servidor.
// Pasa array vacío para que el cliente haga fetch con reintentos.
export default function PedidosPage() {
  return <PedidosPageClient initialPedidos={[]} />;
}
