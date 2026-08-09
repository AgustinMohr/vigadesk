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

import { useEffect, useState } from 'react';
import { pedidoService, type Pedido, type SearchParams } from '@/services/pedidoService';
import Button from '@/components/Button';
import PedidoTable from '@/components/PedidoTable';
import PedidoForm from '@/components/PedidoForm';
import PedidoEstadoModal from '@/components/PedidoEstadoModal';
import PedidoDetalleModal from '@/components/PedidoDetalleModal';
import { useAuth } from '@/lib/auth/AuthContext';

const ESTADOS_PEDIDO = ['PENDIENTE', 'ACEPTADO', 'EN_PREPARACION', 'ENTREGADO', 'CANCELADO'];

export default function PedidosPageClient ({
  initialPedidos,
}: {
  initialPedidos: Pedido[];
}) {
  const { user, isAdmin } = useAuth();

  // Un usuario de cliente solo ve los pedidos de su propia empresa
  const clienteId = user?.rol === 'CLIENTE' ? user.clienteId : undefined;
  const canCreatePedido = isAdmin || user?.rol === 'CLIENTE';

  // Si SSR no trajo datos, reintenta cada 5s hasta obtenerlos
  const [pedidos, setPedidos] = useState<Pedido[]>(initialPedidos);
  const [loading, setLoading] = useState(initialPedidos.length === 0);
  const [fetchError, setFetchError] = useState('');

  const buildParams = (): SearchParams => {
    const params: SearchParams = {};
    if (clienteId != null) params.clienteId = clienteId;
    if (searchCliente.trim()) params.clienteNombre = searchCliente.trim();
    if (searchEstado) params.estado = searchEstado;
    return params;
  };

  useEffect(() => {
    if (initialPedidos.length > 0) return;

    let cancelled = false;

    const fetchData = async () => {
      try {
        const params: SearchParams = {};
        if (clienteId != null) params.clienteId = clienteId;
        const data = await pedidoService.search(params);
        if (!cancelled) {
          setPedidos(data);
          setLoading(false);
        }
      } catch {
        if (!cancelled) setTimeout(fetchData, 5000);
      }
    };

    fetchData();
    return () => { cancelled = true; };
  }, []);

  // Estados para modales y filtros de búsqueda
  const [showForm, setShowForm] = useState(false);
  const [estadoPedido, setEstadoPedido] = useState<Pedido | null>(null);
  const [pedidoDetalle, setPedidoDetalle] = useState<Pedido | null>(null);
  const [searchCliente, setSearchCliente] = useState('');
  const [searchEstado, setSearchEstado] = useState('');

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

  // Refresca la lista completa desde la API (aplica filtros si existen)
  const refreshPedidos = async () => {
    try {
      const data = await pedidoService.search(buildParams());
      setPedidos(data);
    } catch {
      setFetchError('No se pudieron cargar los pedidos.');
    }
  };

  // Búsqueda: obtiene pedidos con filtros desde la API
  const handleSearch = async () => {
    setLoading(true);
    setFetchError('');
    try {
      const data = await pedidoService.search(buildParams());
      setPedidos(data);
    } catch {
      setFetchError('No se pudieron cargar los pedidos.');
    } finally {
      setLoading(false);
    }
  };

  // Elimina pedido previa confirmación
  const handleDelete = async (pedido: Pedido) => {
    if (!window.confirm(`¿Eliminar pedido #${pedido.numeroPedido}?`)) return;
    try {
      await pedidoService.delete(pedido.id!);
      await refreshPedidos();
    } catch {
      alert('No se pudo eliminar el pedido.');
    }
  };

  // Crea un pedido nuevo y refresca la lista
  const handleFormSubmit = async (data: Partial<Pedido>) => {
    await pedidoService.create(data);
    setShowForm(false);
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
  const handleClearFilters = async () => {
    setSearchCliente('');
    setSearchEstado('');
    setLoading(true);
    setFetchError('');
    try {
      const params: SearchParams = {};
      if (clienteId != null) params.clienteId = clienteId;
      const data = await pedidoService.search(params);
      setPedidos(data);
    } catch {
      setFetchError('No se pudieron cargar los pedidos.');
    } finally {
      setLoading(false);
    }
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

      {/* Filtros de búsqueda: nombre de cliente, estado, y botones buscar/limpiar */}
      <div className='mt-6'>
        <div className='mb-4 flex items-end gap-3'>
          <div className='flex-1'>
            <label className='block text-xs font-medium text-slate-500 mb-1'>Cliente</label>
            <input
              placeholder='Nombre del cliente…'
              value={searchCliente}
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
              <p className='text-red-600'>{fetchError}</p>
              )
            : (
              <PedidoTable
                pedidos={pedidos}
                onDelete={handleDelete}
                onUpdateEstado={handleChangeEstado}
                onVer={handleVer}
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
    </section>
  );
}
