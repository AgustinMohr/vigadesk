// ==============================================================
// COMPONENTE CLIENTE de /usuarios (el "cerebro" de la pantalla)
// --------------------------------------------------------------
// Este archivo concentra TODA la lógica interactiva de la página de
// usuarios de cliente: traerlos de la API, filtrarlos por texto,
// abrir el formulario para crear/editar y borrar usuarios.
// Por eso es un Client Component ("use client").
//
// RECORRIDO DE UNA PANTALLA CON DATOS (patrón compartido con obras,
// clientes, pedidos y productos):
//  1. El Server Component (page.tsx) renderiza este cliente con datos
//     iniciales (`initialUsuarios`).
//  2. Si esos datos vienen vacíos, `useEffect` hace un fetch a la API.
//  3. El estado (`usuarios`) guarda la data; cuando cambia, React
//     re-renderiza la tabla.
//  4. `useMemo` recalcula el filtro SOLO cuando cambian sus dependencias.
//  5. El modal (UsuarioForm) se muestra o esconde según variables de
//     estado booleanas.
//
// PARTICULARIDAD: solo un usuario ADMIN puede crear/editar/eliminar
// usuarios de cliente. Los usuarios de tipo CLIENTE solo pueden
// operar sobre pedidos, no sobre esta entidad.
//
// CONCEPTOS CLAVE: Client Component, useState, useEffect con retries,
// useMemo para filtrado derivado, handlers como props, render
// condicional, contexto de autenticación (useAuth), patrón "cancelled"
// para evitar setState tras desmontar.
// ==============================================================

'use client';

import { useMemo, useState } from 'react';
import { usuarioService, type UsuarioCliente } from '@/services/usuarioService';
import Button from '@/components/Button';
import UsuarioTable from '@/components/UsuarioTable';
import UsuarioForm from '@/components/UsuarioForm';
import { useAuth } from '@/lib/auth/AuthContext';
import { useFeedback } from '@/components/Feedback';
import { useFetch } from '@/hooks/useFetch';
import FetchError from '@/components/FetchError';
import { getErrorMessage } from '@/lib/http/api-error';

/**
 * Componente "cerebro" de la página /usuarios: maneja el estado de los
 * usuarios, la carga inicial (con error y "Reintentar"), la
 * búsqueda por nombre/email/cliente y el modal de crear/editar.
 *
 * @param {{ initialUsuarios: UsuarioCliente[] }} props - Datos iniciales
 *   que manda el Server Component (page.tsx). Si vienen vacíos, este
 *   cliente hace el fetch real a la API.
 * @returns {JSX.Element} La sección completa con buscador, tabla y modal.
 */
export default function UsuariosPageClient () {
  // --------------------------------------------------------------
  // useState: estado local del componente.
  //  - `usuarios`: la lista que se muestra en la tabla.
  //  - `loading`: mientras sea true se muestra "Cargando…".
  //  - `fetchError`: mensaje si falla la carga inicial.
  // setUsuarios(...) dispara un re-render y la tabla se actualiza sola.
  // --------------------------------------------------------------
  // Carga al montar y expone carga y error; no reintenta solo (el usuario elige "Reintentar").
  const { data: usuarios, loading, error: fetchError, retryable, reload } = useFetch(
    () => usuarioService.getAll(),
    [] as UsuarioCliente[],
    'No se pudieron cargar los usuarios.'
  );
  // `loading` arranca en true SOLO si el server no trajo datos. Si SSR
  // ya hidrató la lista, no hay que esperar a ningún fetch.

  // --------------------------------------------------------------
  // Estados que controlan el modal del formulario y la búsqueda.
  //  - `showForm`: muestra/oculta el modal.
  //  - `selectedUsuario`: si es undefined → el form crea; si trae un
  //    usuario → el form edita.
  //  - `searchTerm`: texto que escribe el usuario en el buscador.
  // --------------------------------------------------------------
  const [showForm, setShowForm] = useState(false);
  const [selectedUsuario, setSelectedUsuario] = useState<UsuarioCliente | undefined>();
  const [searchTerm, setSearchTerm] = useState('');

  // --------------------------------------------------------------
  // useMemo: guarda en memoria un valor calculado para no recalcularlo
  // en cada render. Filtra la lista por nombre, email o cliente (empresa).
  // Se recalcula SOLO cuando cambian [usuarios, searchTerm]. Sin useMemo,
  // `usuarios.filter(...)` correría en cada render aunque nada cambiara.
  // --------------------------------------------------------------
  const filteredUsuarios = useMemo(() => {
    // Si el buscador está vacío, devolvemos la lista completa tal cual.
    if (!searchTerm.trim()) return usuarios;
    // Normalizamos a minúsculas una sola vez para comparar sin distinguir
    // mayúsculas ("Juan" === "juan").
    const term = searchTerm.toLowerCase().trim();
    return usuarios.filter(
      (u) =>
        u.nombre.toLowerCase().includes(term) ||
        u.email.toLowerCase().includes(term) ||
        // `?.` (optional chaining) y `?? ''` evitan romper si `cliente`
        // es null (un usuario puede no tener empresa asociada).
        (u.cliente?.nombre ?? '').toLowerCase().includes(term)
    );
  }, [usuarios, searchTerm]);

  // useAuth: lee el usuario y su rol desde el Context global de auth.
  // `isAdmin` decide si mostramos el botón "Nuevo usuario".
  const { isAdmin } = useAuth();
  const { avisar, confirmar } = useFeedback();

  // --------------------------------------------------------------
  // HANDLERS: funciones que responden a eventos del usuario y se pasan
  // como props a la tabla y al formulario.
  // --------------------------------------------------------------

  // Abre el form en modo "crear": limpia el usuario seleccionado para que
  // el formulario interprete "no hay usuario → crear uno nuevo".
  const handleCreate = () => {
    setSelectedUsuario(undefined);
    setShowForm(true);
  };

  // Abre el form en modo "editar": guarda el usuario elegido en estado.
  const handleEdit = (usuario: UsuarioCliente) => {
    setSelectedUsuario(usuario);
    setShowForm(true);
  };

  // Vuelve a pedir TODOS los usuarios a la API. Se invoca después de
  // crear, editar o borrar para reflejar los cambios en pantalla.
  const refreshUsuarios = () => reload({ silent: true });

  // Borra un usuario previa confirmación (diálogo accesible).
  const handleDelete = async (usuario: UsuarioCliente) => {
    if (!(await confirmar({ titulo: 'Eliminar usuario', mensaje: `¿Eliminar al usuario "${usuario.nombre}"?`, confirmar: 'Eliminar', peligro: true }))) return;
    try {
      // `id!` (non-null assertion): le decimos a TypeScript "confío en que
      // existe", aunque el tipo lo declare opcional. Si viniera null, la
      // API rechazaría la petición y caería en el catch.
      await usuarioService.delete(usuario.id!);
      await refreshUsuarios();
    } catch (err) {
      avisar('error', getErrorMessage(err, 'No se pudo eliminar el usuario.'));
    }
  };

  // Crea o actualiza según si hay un usuario seleccionado, luego refresca.
  // `data` es una vista PARCIAL del usuario (solo campos editables).
  const handleFormSubmit = async (data: Partial<UsuarioCliente>) => {
    if (selectedUsuario) {
      // Hay selección → actualizar (PUT/PATCH al backend).
      await usuarioService.update(selectedUsuario.id!, data);
    } else {
      // Sin selección → crear (POST al backend).
      await usuarioService.create(data);
    }
    // Cierra el modal y limpia la selección pase lo que pase.
    setShowForm(false);
    setSelectedUsuario(undefined);
    await refreshUsuarios();
  };

  // Cancela el formulario sin guardar.
  const handleFormCancel = () => {
    setShowForm(false);
    setSelectedUsuario(undefined);
  };

  return (
    <section>
      {/* Encabezado: título + botón de crear (solo admin) */}
      <div className='flex items-center justify-between'>
        <div>
          <h2 className='text-2xl font-semibold'>Usuarios de clientes</h2>
          <p className='mt-2 text-slate-600'>
            Usuarios que operan en nombre de un cliente (hacen pedidos por su empresa).
          </p>
        </div>
        {isAdmin && <Button onClick={handleCreate}>Nuevo usuario</Button>}
      </div>

      <div className='mt-6'>
        {/* Buscador: actualiza `searchTerm` en cada tecla; el filtrado
            real lo hace `filteredUsuarios` vía useMemo. */}
        <div className='mb-4 flex items-center gap-3'>
          <input
            type='text'
            aria-label='Buscar por nombre, email o cliente' placeholder='Buscar por nombre, email o cliente…'
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className='w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
          />
        </div>

        {/* Render condicional con 3 estados: cargando / error / tabla.
            El operador ternario anidado prioriza loading sobre error. */}
        {loading
          ? (
            <p className='text-slate-400'>Cargando usuarios…</p>
            )
          : fetchError
            ? (
              <FetchError mensaje={fetchError} onRetry={retryable ? () => reload() : undefined} />
              )
            : (
              <UsuarioTable
                usuarios={filteredUsuarios}
                onEdit={handleEdit}
                onDelete={handleDelete}
              />
              )}
      </div>

      {/* Modal: se monta (aparece) solo si `showForm` es true.
          El patrón es pasar callbacks (onSubmit/onCancel) para que el
          hijo avise "se guardó" o "cerrame". */}
      {showForm && (
        <UsuarioForm
          usuario={selectedUsuario}
          onSubmit={handleFormSubmit}
          onCancel={handleFormCancel}
        />
      )}
    </section>
  );
}
