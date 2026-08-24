// ==============================================================
// LAYOUT del grupo de rutas "(app)" — las páginas "protegidas"
// --------------------------------------------------------------
// La carpeta `(app)` usa PARÉNTESIS: en Next.js eso es un "route group",
// una agrupación lógica que NO agrega segmento a la URL. Es decir,
// `app/(app)/obras/page.tsx` responde en "/obras" (no "/app/obras").
// Todas las páginas que viven acá comparten este layout, que las
// envuelve con <AuthLayout> (protección de sesión + navbar/sidebar).
// ==============================================================

import AuthLayout from './AuthLayout';

export default function AppLayout ({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return <AuthLayout>{children}</AuthLayout>;
}
