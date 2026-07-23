// Esta pagina esta fuera del grupo "(app)", por eso no usa Navbar ni Sidebar.
// Su URL es /login porque el archivo esta en src/app/login/page.tsx.
export default function LoginPage() {
  return (
    // Centramos la tarjeta de login ocupando toda la altura de la pantalla.
    <main className="flex min-h-screen items-center justify-center bg-slate-50 px-4">
      <section className="w-full max-w-sm rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-semibold text-slate-950">Iniciar sesion</h1>
        <p className="mt-2 text-sm text-slate-600">
          Ingresa tus datos para acceder al sistema.
        </p>

        {/* Todavia no tiene onSubmit: por ahora es solo UI. */}
        <form className="mt-6 space-y-4">
          <div>
            <label
              htmlFor="email"
              className="block text-sm font-medium text-slate-700"
            >
              Email
            </label>
            <input
              id="email"
              name="email"
              type="email"
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-500"
              placeholder="usuario@email.com"
            />
          </div>

          <div>
            <label
              htmlFor="password"
              className="block text-sm font-medium text-slate-700"
            >
              Contrasena
            </label>
            <input
              id="password"
              name="password"
              type="password"
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-500"
              placeholder="********"
            />
          </div>

          <button
            type="submit"
            className="w-full rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800"
          >
            Entrar
          </button>
        </form>
      </section>
    </main>
  );
}
