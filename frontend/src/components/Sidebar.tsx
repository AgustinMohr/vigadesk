import Link from "next/link";

// Centralizamos las opciones del menu en un array.
// Asi, para agregar una pantalla nueva, alcanza con sumar otro objeto aca.
const links = [
  { href: "/dashboard", label: "Dashboard" },
  { href: "/clientes", label: "Clientes" },
  { href: "/productos", label: "Productos" },
  { href: "/pedidos", label: "Pedidos" },
];

export default function Sidebar() {
  return (
    <aside className="w-64 border-r border-slate-200 bg-white px-4 py-6">
      <ul className="space-y-1">
        {/* Recorremos el array y generamos un item de menu por cada ruta. */}
        {links.map((link) => (
          <li key={link.href}>
            {/* Link es el componente de Next para navegar sin recargar toda la pagina. */}
            <Link
              href={link.href}
              className="block rounded-md px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100 hover:text-slate-950"
            >
              {link.label}
            </Link>
          </li>
        ))}
      </ul>
    </aside>
  );
}
