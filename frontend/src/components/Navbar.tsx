import Link from "next/link";

// Navbar superior compartido por las pantallas internas.
// Se importa desde src/app/(app)/layout.tsx, por eso no aparece en /login.
export default function Navbar() {
  return (
    <nav className="flex h-16 items-center justify-between border-b border-slate-200 bg-white px-6">
      {/* El nombre del sistema tambien funciona como acceso rapido al dashboard. */}
      <Link href="/dashboard" className="text-lg font-semibold text-slate-950">
        Mi Sistema
      </Link>

      {/* Por ahora "Salir" solo navega a /login; mas adelante puede limpiar sesion/token. */}
      <Link
        href="/login"
        className="rounded-md border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100"
      >
        Salir
      </Link>
    </nav>
  );
}
