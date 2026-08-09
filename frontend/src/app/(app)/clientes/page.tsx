// ==============================================================
// Página /clientes (Server Component)
// Mismo patrón que /obras: el server solo monta al Client Component
// con datos iniciales vacíos; el cliente hace el fetch con reintentos.
// ==============================================================
import ClientesPageClient from './ClientesPageClient';

export default function ClientesPage () {
  return <ClientesPageClient initialClientes={[]} />;
}
