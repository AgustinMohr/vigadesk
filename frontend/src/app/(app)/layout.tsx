import Navbar from "@/components/Navbar";
import Sidebar from "@/components/Sidebar";

// Layout para rutas protegidas/internas: Navbar + Sidebar + contenido.
// El grupo (app) no aparece en la URL: /clientes, /dashboard, etc.
export default function AppLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <div className="min-h-screen bg-slate-50 text-slate-950">
      <Navbar />
      <div className="flex min-h-[calc(100vh-64px)]">
        <Sidebar />
        <main className="flex-1 p-6">
          {children}
        </main>
      </div>
    </div>
  );
}
