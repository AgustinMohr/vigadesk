// ==============================================================
// Página /clientes (Server Component)
// --------------------------------------------------------------
// Mismo patrón que /obras: el server solo monta al Client Component
// con datos iniciales vacíos; el cliente carga los datos.
//
// PATRÓN SERVER → CLIENT: este archivo corre en el servidor (sin
// "use client") y su único trabajo es pasar props iniciales al componente
// cliente (<ClientesPageClient>). El SSR NO consulta la API (evita
// duplicar llamadas); quien trae los datos reales es el cliente.
// ==============================================================

import ClientesPageClient from './ClientesPageClient';

/**
 * Página /clientes: Server Component que monta el cliente interactivo.
 *
 * @returns {JSX.Element} El <ClientesPageClient> con la lista inicial vacía.
 */
export default function ClientesPage () {
  return <ClientesPageClient />;
}
