// ==============================================================
// PÁGINA /dashboard (Server Component)
// Componente servidor: no tiene lógica propia, solo "monta" al
// componente cliente que hace todo el trabajo (mismo patrón que
// /obras, ver obras/page.tsx). Server Component = se ejecuta en el
// servidor y manda HTML listo; si necesitás interactividad, delegás
// a un componente con "use client".
// ==============================================================

// Página /dashboard: Server Component que solo "monta" al cliente que
// hace todo el trabajo (mismo patrón que /obras, ver page.tsx de obras).
import DashboardClient from './DashboardClient';

export default function DashboardPage () {
  return <DashboardClient />;
}
