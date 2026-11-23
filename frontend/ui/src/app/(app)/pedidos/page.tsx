// ==============================================================
// Página /pedidos (Server Component)
// --------------------------------------------------------------
// Mismo patrón que /obras: el server solo monta al Client Component
// <PedidosPageClient> con la lista inicial vacía; el cliente decide
// qué pedidos traer según el rol (admin ve todo; un usuario CLIENTE
// solo los de su empresa) y carga los datos.
// ==============================================================

import PedidosPageClient from './PedidosPageClient';

/**
 * Página /pedidos: Server Component que monta el cliente interactivo.
 *
 * @returns {JSX.Element} El <PedidosPageClient> con la lista inicial vacía.
 */
export default function PedidosPage () {
  return <PedidosPageClient />;
}
