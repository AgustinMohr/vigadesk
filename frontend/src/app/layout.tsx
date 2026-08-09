// ==============================================================
// LAYOUT RAÍZ de Next.js (App Router)
// --------------------------------------------------------------
// En Next.js, un archivo `layout.tsx` define una "plantilla" que
// envuelve a TODAS las páginas que cuelgan de él. Como este vive en
// `src/app/`, es el layout más externo: /login, /dashboard, /clientes,
// etc. se renderizan dentro de {children}.
// Dato clave: los layouts se mantienen montados al navegar entre
// páginas hijas (el header/estructura común no se re-renderiza).
// ==============================================================

import type { Metadata } from 'next';
import { Geist, Geist_Mono as GeistMono } from 'next/font/google';
import './globals.css';

const geistSans = Geist({
  variable: '--font-geist-sans',
  subsets: ['latin'],
});

const geistMono = GeistMono({
  variable: '--font-geist-mono',
  subsets: ['latin'],
});

// Metadatos globales: título y descripción que Next coloca en el <head>
// de todas las páginas (SEO / pestaña del navegador).
export const metadata: Metadata = {
  title: 'Mi Sistema',
  description: 'Sistema de gestion',
};

// Componente del layout raíz. Recibe `children` (las páginas hijas) como
// prop automáticamente. Al ser un Server Component (no tiene "use client"),
// se renderiza en el servidor: el HTML llega ya "precocido" al navegador.
export default function RootLayout ({
  children,
}: Readonly<{
  children: React.ReactNode; // "React.ReactNode" = cualquier cosa renderizable
}>) {
  return (
    <html
      lang='es'
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
    >
      <body className='min-h-full'>
        {/* children = el contenido de la página actual */}
        {children}
      </body>
    </html>
  );
}
