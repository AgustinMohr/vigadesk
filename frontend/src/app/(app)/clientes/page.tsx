import ClientesPageClient from "./ClientesPageClient";

// Server Component: se renderiza en el servidor y envía el HTML al navegador.
// Pasa un array vacío como initialClientes para que el cliente haga fetch con reintentos.
export default function ClientesPage() {
  return <ClientesPageClient initialClientes={[]} />;
}
