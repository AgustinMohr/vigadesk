// ==============================================================
// COMPONENTE CLIENTE de /pedidos
// Es la MISMA estructura que ObrasPageClient.tsx (ver ahí la explicación
// de useState/useEffect/handlers/modales). Particularidades:
//  - Según el ROL: un usuario CLIENTE solo ve y crea pedidos de su propia
//    empresa (clienteId viene del usuario logueado en useAuth). El admin
//    ve todo y además puede buscar por cliente/estado.
//  - La búsqueda se hace EN EL BACKEND vía pedidoService.search(...) con
//    filtros (a diferencia de obras/clientes que filtran en el cliente).
// ==============================================================

'use client';

import { useState } from 'react';
import {
  pedidoService,
  type CrearPedidoInput,
  type EntregaInput,
  type Pedido,
  type SearchParams,
} from '@/services/pedidoService';
import Button from '@/components/Button';
import PedidoTable from '@/components/PedidoTable';
import PedidoForm from '@/components/PedidoForm';
import PedidoEstadoModal from '@/components/PedidoEstadoModal';
import PedidoDetalleModal from '@/components/PedidoDetalleModal';
import EntregaModal from '@/components/EntregaModal';
import { useAuth } from '@/lib/auth/AuthContext';
import { useFetch } from '@/hooks/useFetch';
import FetchError from '@/components/FetchError';
import { getErrorMessage } from '@/lib/http/api-error';

const ESTADOS_PEDIDO = ['RECIBIDO', 'ACEPTADO', 'EN_PREPARACION', 'ENTREGA_PARCIAL', 'ENTREGADO', 'CANCELADO', 'RECHAZADO'];

type Aviso = { tipo: 'ok' | 'warn' | 'error'; texto: string };

// Resultado de crear un pedido: creado, rechazado por crédito o pendiente de stock.
function avisoDeCreacion (pedido: Pedido): Aviso {
  const n = pedido.numeroPedido;
  if (pedido.estado === 'RECHAZADO') {
    return { tipo: 'error', texto: `Pedido #${n} rechazado: crédito insuficiente.` };
  }
  if (pedido.estado === 'ACEPTADO') {
    return { tipo: 'warn', texto: `Pedido #${n} creado, pendiente de stock. Un administrador puede reintentar la reserva.` };
  }
  return { tipo: 'ok', texto: `Pedido #${n} creado y stock reservado (${pedido.estado}).` };
}

const avisoClasses: Record<Aviso['tipo'], string> = {
  ok: 'border-green-300 bg-green-50 text-green-800',
  warn: 'border-amber-300 bg-amber-50 text-amber-800',
  error: 'border-red-300 bg-red-50 text-red-800',
};

/**
 * Componente "cerebro" de la página /pedidos.
 *
 * Particularidad por rol: un usuario CLIENTE solo ve/crea pedidos de su
 * propia empresa (clienteId sale de la sesión vía useAuth) y la búsqueda
 * se hace EN EL BACKEND con pedidoService.search(...) — a diferencia de
 * obras/clientes que filtran en el cliente con useMemo.
 *
 * @param {{ initialPedidos: Pedido[] }} props - Datos iniciales del Server
 *   Component.
 * @returns {JSX.Element} La sección con filtros, tabla y modales.
 */
export default function PedidosPageClient () {
  const { user, isAdmin } = useAuth();

  // Un usuario de cliente solo ve los pedidos de su propia empresa
  const clienteId = user?.rol === 'CLIENTE' ? user.clienteId : undefined;
  const canCreatePedido = isAdmin || user?.rol === 'CLIENTE';

  // Carga al montar y expone carga y error; no reintenta solo (el usuario elige "Reintentar").
  const { data: pedidos, loading, error: fetchError, retryable, reload } = useFetch(
    () => pedidoService.search(buildParams()),
    [] as Pedido[],
    'No se pudieron cargar los pedidos.'
  );

  const buildParams = (): SearchParams => {
    const params: SearchParams = {};
    if (clienteId != null) params.clienteId = clienteId;
    if (searchCliente.trim()) params.clienteNombre = searchCliente.trim();
    if (searchEstado) params.estado = searchEstado;
    return params;
  };

  // Estados para modales y filtros de búsqueda
  const [showForm, setShowForm] = useState(false);
  const [estadoPedido, setEstadoPedido] = useState<Pedido | null>(null);
  const [pedidoDetalle, setPedidoDetalle] = useState<Pedido | null>(null);
  const [pedidoEntrega, setPedidoEntrega] = useState<Pedido | null>(null);
  const [searchCliente, setSearchCliente] = useState('');
  const [searchEstado, setSearchEstado] = useState('');
  const [aviso, setAviso] = useState<Aviso | null>(null);

  const handleCreate = () => {
    setShowForm(true);
  };

  // Abre modal de cambio de estado para pedidos ACEPTADO o EN_PREPARACION
  const handleChangeEstado = (pedido: Pedido) => {
    setEstadoPedido(pedido);
  };

  const handleVer = (pedido: Pedido) => {
    setPedidoDetalle(pedido);
  };

  const handleEntregar = (pedido: Pedido) => {
    setPedidoEntrega(pedido);
  };

  const handleEntregaSubmit = async (entrega: EntregaInput) => {
    if (!pedidoEntrega) return;
    await pedidoService.createEntrega(pedidoEntrega.id!, entrega);
    setPedidoEntrega(null);
    await refreshPedidos();
  };

  // Refresca la lista completa desde la API (aplica filtros si existen)
  const refreshPedidos = () => reload({ silent: true });

  // Búsqueda: obtiene pedidos con filtros desde la API
  const handleSearch = () => reload();

  // Elimina pedido previa confirmación
  const handleDelete = async (pedido: Pedido) => {
    if (!window.confirm(`¿Eliminar pedido #${pedido.numeroPedido}?`)) return;
    try {
      await pedidoService.delete(pedido.id!);
      await refreshPedidos();
    } catch (err) {
      alert(getErrorMessage(err, 'No se pudo eliminar el pedido.'));
    }
  };

  // Crea un pedido nuevo y refresca la lista
  const handleFormSubmit = async (data: CrearPedidoInput, idempotencyKey: string) => {
    const creado = await pedidoService.create(data, idempotencyKey);
    setShowForm(false);
    setAviso(avisoDeCreacion(creado));
    await refreshPedidos();
  };

  // Reintenta la reserva de stock de un pedido pendiente (solo ADMIN)
  const handleReintentarReserva = async (pedido: Pedido) => {
    try {
      const actualizado = await pedidoService.reintentarReserva(pedido.id!);
      setAviso({
        tipo: 'ok',
        texto: `Pedido #${actualizado.numeroPedido}: stock reservado, pasó a ${actualizado.estado}.`,
      });
    } catch (err) {
      setAviso({ tipo: 'error', texto: getErrorMessage(err, 'No se pudo reintentar la reserva.') });
    }
    await refreshPedidos();
  };

  const handleFormCancel = () => {
    setShowForm(false);
  };

  // Actualiza estado (ej: ACEPTADO → EN_PREPARACION → ENTREGADO, o CANCELADO)
  const handleEstadoSubmit = async (nuevoEstado: string) => {
    if (!estadoPedido) return;
    await pedidoService.updateEstado(estadoPedido.id!, nuevoEstado);
    setEstadoPedido(null);
    await refreshPedidos();
  };

  // Limpia filtros de búsqueda y recarga sin filtros
  const handleClearFilters = () => {
    setSearchCliente('');
    setSearchEstado('');
    // los filtros del estado todavía no se actualizaron en este render: se piden sin ellos
    return reload({ fetcher: () => pedidoService.search(clienteId != null ? { clienteId } : {}) });
  };

  return (
    <section>
      {/* Encabezado */}
      <div className='flex items-center justify-between'>
        <div>
          <h2 className='text-2xl font-semibold'>Pedidos</h2>
          <p className='mt-2 text-slate-600'>
            Gestión de pedidos de clientes.
          </p>
        </div>
        {canCreatePedido && <Button onClick={handleCreate}>Nuevo pedido</Button>}
      </div>

      {aviso && (
        <div
          role='status'
          className={`mt-4 flex items-start justify-between rounded border px-4 py-3 text-sm ${avisoClasses[aviso.tipo]}`}
        >
          <span>{aviso.texto}</span>
          <button onClick={() => setAviso(null)} aria-label='Cerrar aviso' className='ml-4 font-medium'>
            ×
          </button>
        </div>
      )}

      {/* Filtros de búsqueda: nombre de cliente, estado, y botones buscar/limpiar */}
      <div className='mt-6'>
        <div className='mb-4 flex items-end gap-3'>
          <div className='flex-1'>
            <label className='block text-xs font-medium text-slate-500 mb-1'>Cliente</label>
            <input
              placeholder='Nombre del cliente…'
              value={searchCliente}
              maxLength={100}
              onChange={(e) => setSearchCliente(e.target.value)}
              className='w-full rounded border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
            />
          </div>
          <div>
            <label className='block text-xs font-medium text-slate-500 mb-1'>Estado</label>
            <select
              value={searchEstado}
              onChange={(e) => setSearchEstado(e.target.value)}
              className='rounded border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
            >
              <option value=''>Todos</option>
              {ESTADOS_PEDIDO.map((est) => (
                <option key={est} value={est}>{est}</option>
              ))}
            </select>
          </div>
          <Button onClick={handleSearch}>Buscar</Button>
          <button
            onClick={handleClearFilters}
            className='rounded border border-slate-300 px-3 py-2 text-sm text-slate-600 hover:bg-slate-100 transition'
          >
            Limpiar
          </button>
        </div>

        {/* Tres estados: cargando, error, tabla */}
        {loading
          ? (
            <p className='text-slate-400'>Cargando pedidos…</p>
            )
          : fetchError
            ? (
              <FetchError mensaje={fetchError} onRetry={retryable ? () => reload() : undefined} />
              )
            : (
              <PedidoTable
                pedidos={pedidos}
                onDelete={handleDelete}
                onUpdateEstado={handleChangeEstado}
                onVer={handleVer}
                onEntregar={handleEntregar}
                onReintentarReserva={handleReintentarReserva}
              />
              )}
      </div>

      {/* Modales: formulario de pedido y cambio de estado */}
      {showForm && (
        <PedidoForm
          onSubmit={handleFormSubmit}
          onCancel={handleFormCancel}
        />
      )}

      {estadoPedido && (
        <PedidoEstadoModal
          pedidoNumero={estadoPedido?.numeroPedido}
          onSubmit={handleEstadoSubmit}
          onCancel={() => setEstadoPedido(null)}
        />
      )}

      {pedidoDetalle && (
        <PedidoDetalleModal
          pedido={pedidoDetalle}
          onClose={() => setPedidoDetalle(null)}
        />
      )}

      {pedidoEntrega && (
        <EntregaModal
          pedido={pedidoEntrega}
          onSubmit={handleEntregaSubmit}
          onCancel={() => setPedidoEntrega(null)}
        />
      )}
    </section>
  );
}
