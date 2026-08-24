// ==============================================================
// Página /usuarios (Server Component)
// --------------------------------------------------------------
// Mismo patrón que /obras: el server solo monta al Client Component
// <UsuariosPageClient> con la lista inicial vacía; el cliente hace
// el fetch con reintentos. Esta pantalla es de uso exclusivo del admin:
// administra los usuarios de clientes (quién puede operar en nombre de
// cada empresa).
// ==============================================================

import UsuariosPageClient from './UsuariosPageClient';

/**
 * Página /usuarios: Server Component que monta el cliente interactivo.
 *
 * @returns {JSX.Element} El <UsuariosPageClient> con la lista inicial vacía.
 */
export default function UsuariosPage () {
  return <UsuariosPageClient initialUsuarios={[]} />;
}
