// ==============================================================
// Página /usuarios (Server Component)
// Mismo patrón que /obras: el server solo monta al Client Component.
// Esta pantalla es de uso exclusivo del admin: administra los usuarios
// de clientes (quién puede operar en nombre de cada empresa).
// ==============================================================
import UsuariosPageClient from './UsuariosPageClient';

export default function UsuariosPage () {
  return <UsuariosPageClient initialUsuarios={[]} />;
}
