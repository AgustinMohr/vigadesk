// ==============================================================
// Página /dashboard (Server Component)
// --------------------------------------------------------------
// Componente servidor: no tiene lógica propia, solo "monta" al
// componente cliente que hace todo el trabajo (mismo patrón que
// /obras, ver obras/page.tsx). Server Component = se ejecuta en el
// servidor y manda HTML listo; si necesitás interactividad, delegás
// a un componente con "use client".
// ==============================================================

import DashboardClient from './DashboardClient';

/**
 * Página /dashboard: Server Component que monta el cliente interactivo.
 * El DashboardClient trae datos de los 4 microservicios en paralelo.
 *
 * @returns {JSX.Element} El <DashboardClient> (sin props iniciales).
 */
export default function DashboardPage () {
  return <DashboardClient />;
}
