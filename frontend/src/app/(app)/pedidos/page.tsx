// ==============================================================
// Página /pedidos (Server Component)
// Mismo patrón que /obras: el server solo monta al Client Component.
// ==============================================================
import PedidosPageClient from './PedidosPageClient';

export default function PedidosPage () {
  return <PedidosPageClient initialPedidos={[]} />;
}
