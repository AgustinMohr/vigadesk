// ==============================================================
// COMPONENTE CLIENTE de la página /obras (el "cerebro" de la pantalla)
// --------------------------------------------------------------
// Este archivo concentra TODA la lógica interactiva de la página de
// obras: traer datos de la API, filtrarlos, abrir modales, crear/editar/
// eliminar obras. Por eso es un Client Component ("use client").
//
// RECORRIDO DE UNA PANTALLA CON DATOS (patrón que se repite en clientes,
// pedidos, productos y usuarios):
//  1. El Server Component (page.tsx) renderiza este cliente con datos iniciales.
//  2. Si esos datos están vacíos, `useEffect` hace un fetch a la API.
//  3. El estado (`obras`) guarda la data; cuando cambia, React re-renderiza.
//  4. `useMemo` recalcula el filtro SOLO cuando cambian sus dependencias.
//  5. Los modales (ObraForm, ObraEstadoModal, AsignarClienteModal) se
//     muestran o esconden según variables de estado booleanas/nulas.
// ==============================================================

'use client';

import { useEffect, useMemo, useState } from 'react';
import { obraService, type Obra, type EstadoObra } from '@/services/obraService';
import Button from '@/components/Button';
import ObraTable from '@/components/ObraTable';
import ObraForm from '@/components/ObraForm';
import ObraEstadoModal from '@/components/ObraEstadoModal';
import AsignarClienteModal from '@/components/AsignarClienteModal';
import { useAuth } from '@/lib/auth/AuthContext';
import { getErrorMessage } from '@/lib/http/api-error';

/**
 * Componente "cerebro" de la página /obras: concentra todo el flujo de
 * datos (fetch con reintentos), filtros, handlers de CRUD y la apertura
 * condicional de modales (form, estado, asignación de cliente).
 *
 * @param {{ initialObras: Obra[] }} props - Datos iniciales del Server
 *   Component. Vacíos → el cliente hace el fetch real a la API.
 * @returns {JSX.Element} La sección con filtros, tabla y modales.
 */
export default function ObrasPageClient ({
  initialObras,
}: {
  initialObras: Obra[];
}) {
  // --------------------------------------------------------------
  // useState: estado local del componente.
  //  - `obras`: la lista de obras que se muestra.
  //  - `loading`: si está cargando (para mostrar "Cargando…").
  //  - `fetchError`: mensaje si falló la carga inicial.
  // Cuando llamamos a setObras(...), React vuelve a renderizar y la
  // tabla se actualiza automáticamente.
  // --------------------------------------------------------------
  const [obras, setObras] = useState<Obra[]>(initialObras);
  const [loading, setLoading] = useState(initialObras.length === 0);
  const [fetchError, setFetchError] = useState('');

  // --------------------------------------------------------------
  // useEffect: corre código DESPUÉS de que el componente se monta.
  // Acá, si el server no trajo obras, hacemos el fetch real a la API.
  // La variable `cancelled` es el patrón estándar para no hacer
  // setState si el componente ya se desmontó (evita "memory leak").
  // Si falla, se reintenta cada 5 segundos.
  // El array vacío `[]` = "ejecutar solo una vez al montar".
  // --------------------------------------------------------------
  useEffect(() => {
    if (initialObras.length > 0) return;

    let cancelled = false;

    const fetchData = async () => {
      try {
        const data = await obraService.getAll();
        if (!cancelled) {
          setObras(data);
          setLoading(false);
        }
      } catch {
        if (!cancelled) setTimeout(fetchData, 5000); // reintento
      }
    };

    fetchData();
    return () => { cancelled = true; }; // cleanup al desmontar
  }, []);

  // Estados que controlan los modales y la búsqueda
  const [showForm, setShowForm] = useState(false);
  const [selectedObra, setSelectedObra] = useState<Obra | undefined>();
  const [obraEstado, setObraEstado] = useState<Obra | null>(null);
  const [obraAssign, setObraAssign] = useState<Obra | null>(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [searchEstado, setSearchEstado] = useState<EstadoObra | ''>('');

  // --------------------------------------------------------------
  // useMemo: guarda en memoria un valor calculado.
  // Filtra la lista por texto (dirección o cliente) y por estado.
  // IMPORTANTE: se recalcula SOLO cuando cambian [obras, searchTerm,
  // searchEstado]. Sin useMemo, se recalcularía en cada render.
  // --------------------------------------------------------------
  const filteredObras = useMemo(() => {
    return obras.filter((o) => {
      if (
        searchTerm.trim() &&
        !o.direccion.toLowerCase().includes(searchTerm.toLowerCase().trim()) &&
        !(o.cliente?.nombre ?? '') // "?." evita romper si cliente es null
          .toLowerCase()
          .includes(searchTerm.toLowerCase().trim())
      ) {
        return false;
      }
      if (searchEstado && o.estado !== searchEstado) return false;
      return true;
    });
  }, [obras, searchTerm, searchEstado]);

  // useAuth: lee el usuario del Context global de autenticación.
  const { isAdmin } = useAuth();

  // --------------------------------------------------------------
  // HANDLERS: funciones que responden a eventos del usuario.
  // Se pasan como props a la tabla y a los formularios.
  // Ej: handleDelete recibe una obra, confirma, borra y refresca.
  // --------------------------------------------------------------
  const handleCreate = () => {
    setSelectedObra(undefined); // sin obra → el form crea una nueva
    setShowForm(true);
  };

  const handleEdit = (obra: Obra) => {
    setSelectedObra(obra); // con obra → el form edita
    setShowForm(true);
  };

  // Vuelve a pedir todas las obras a la API (se usa tras crear/editar/borrar).
  const refreshObras = async () => {
    try {
      const data = await obraService.getAll();
      setObras(data);
    } catch (err) {
      setFetchError(getErrorMessage(err, 'No se pudieron cargar las obras.'));
    }
  };

  const handleDelete = async (obra: Obra) => {
    if (!window.confirm(`¿Eliminar la obra "${obra.direccion}"?`)) return;
    try {
      await obraService.delete(obra.id!);
      await refreshObras();
    } catch (err) {
      alert(getErrorMessage(err, 'No se pudo eliminar la obra.'));
    }
  };

  // Crea o actualiza según si hay una obra seleccionada.
  const handleFormSubmit = async (data: Partial<Obra>) => {
    if (selectedObra) {
      await obraService.update(selectedObra.id!, data);
    } else {
      await obraService.create(data);
    }
    setShowForm(false);
    setSelectedObra(undefined);
    await refreshObras();
  };

  const handleFormCancel = () => {
    setShowForm(false);
    setSelectedObra(undefined);
  };

  // --------------------------------------------------------------
  // IMPORTANTE (bug que arreglamos): antes era `obraEstado!.id!`.
  // El React Compiler genera una clave de caché evaluando esa expresión
  // en CADA render, y como `obraEstado` arranca en null, explotaba con
  // "Cannot read properties of null". Con `obraEstado?.id` (optional
  // chaining) la lectura es segura: si es null, devuelve undefined.
  // --------------------------------------------------------------
  const handleEstadoSubmit = async (estado: EstadoObra) => {
    const obraId = obraEstado?.id;
    if (obraId == null) return; // no hay obra seleccionada → no hacer nada
    await obraService.cambiarEstado(obraId, estado);
    setObraEstado(null);
    await refreshObras();
  };

  const handleEstadoCancel = () => {
    setObraEstado(null);
  };

  return (
    <section>
      {/* Encabezado: título + botón de crear (solo admin) */}
      <div className='flex items-center justify-between'>
        <div>
          <h2 className='text-2xl font-semibold'>Obras</h2>
          <p className='mt-2 text-slate-600'>
            Gestión de obras registradas.
          </p>
        </div>
        {isAdmin && <Button onClick={handleCreate}>Nueva obra</Button>}
      </div>

      <div className='mt-6'>
        {/* Filtros: texto (dirección/cliente) y estado */}
        <div className='mb-4 flex items-center gap-3'>
          <input
            type='text'
            placeholder='Buscar por dirección o cliente…'
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className='flex-1 rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
          />
          <select
            value={searchEstado}
            onChange={(e) => setSearchEstado(e.target.value as EstadoObra | '')}
            className='rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
          >
            <option value=''>Todos los estados</option>
            {(['HABILITADA', 'PENDIENTE', 'FINALIZADA'] as EstadoObra[]).map(
              (e) => (
                <option key={e} value={e}>
                  {e}
                </option>
              )
            )}
          </select>
        </div>

        {/* Render condicional con 3 estados: cargando / error / tabla */}
        {loading
          ? (
            <p className='text-slate-400'>Cargando obras…</p>
            )
          : fetchError
            ? (
              <p className='text-red-600'>{fetchError}</p>
              )
            : (
              <ObraTable
                obras={filteredObras}
                onEdit={handleEdit}
                onDelete={handleDelete}
                onEstado={setObraEstado}
                onAsignarCliente={setObraAssign}
              />
              )}
      </div>

      {/* Modales: se montan (aparecen) solo si su variable es "truthy".
          El patrón es: pasar callbacks (onSubmit/onCancel) para que el
          hijo avise "se guardó" o "cerrame". */}
      {showForm && (
        <ObraForm
          obra={selectedObra}
          onSubmit={handleFormSubmit}
          onCancel={handleFormCancel}
        />
      )}

      {obraEstado && (
        <ObraEstadoModal
          obraDireccion={obraEstado.direccion}
          onSubmit={handleEstadoSubmit}
          onCancel={handleEstadoCancel}
        />
      )}

      {obraAssign && (
        <AsignarClienteModal
          obra={obraAssign}
          onAssign={refreshObras}
          onClose={() => setObraAssign(null)}
        />
      )}
    </section>
  );
}
