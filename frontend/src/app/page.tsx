// ==============================================================
// Página de inicio "/" (Server Component)
// --------------------------------------------------------------
// Un `page.tsx` dentro de `src/app/` define la ruta "/" (la raíz).
// Por convención, el archivo `page.tsx` = la URL, `layout.tsx` =
// la plantilla que la envuelve.
//
// Este es un Server Component (sin "use client"): corre en el servidor
// y, como no tiene que devolver HTML propio, solo ordena una redirección.
// `redirect()` es de Next.js: corta la ejecución y responde con un
// 307/308 hacia la URL indicada. Por eso escribir "localhost/" te lleva
// automáticamente al dashboard.
// ==============================================================

import { redirect } from 'next/navigation';

export default function HomePage () {
  redirect('/dashboard');
}
