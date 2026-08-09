// ==============================================================
// TABLA DE PEDIDOS
// Mismo patrón que ObraTable.tsx. Particularidades:
//  - getPedidoValue: función para ordenar columnas cuyo valor no está
//    en la raíz del pedido (ej: "clienteNombre" vive en pedido.cliente).
//  - El botón "Estado" solo aparece si el pedido está ACEPTADO o
//    EN_PREPARACION (esos estados permiten avanzar el flujo).
//  - Formatea fecha y moneda con helpers locales.
// ==============================================================

'use client';

import type { Pedido } from '@/services/pedidoService';
import Button from './Button';
import { useSort } from '@/hooks/useSort';
import { useAuth } from '@/lib/auth/AuthContext';

const thClass =
  'px-4 py-3 text-left font-medium text-slate-600 cursor-pointer select-none hover:text-slate-900';
const thClassRight =
  'px-4 py-3 text-right font-medium text-slate-600 cursor-pointer select-none hover:text-slate-900';

const estadoColors: Record<string, string> = {
  RECIBIDO: 'text-yellow-700 bg-yellow-100',
  ACEPTADO: 'text-blue-700 bg-blue-100',
  EN_PREPARACION: 'text-indigo-700 bg-indigo-100',
  ENTREGADO: 'text-green-700 bg-green-100',
  CANCELADO: 'text-red-700 bg-red-100',
  RECHAZADO: 'text-gray-700 bg-gray-100',
};

function formatDate (iso?: string) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('es-AR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function formatCurrency (n?: number) {
  if (n == null) return '—';
  return `$${n.toLocaleString('es-AR', { minimumFractionDigits: 2 })}`;
}

type PedidoTableProps = {
  pedidos: Pedido[];
  onUpdateEstado: (pedido: Pedido) => void;
  onDelete: (pedido: Pedido) => void;
  onVer: (pedido: Pedido) => void;
};

function getPedidoValue (item: Pedido, key: string): string | number {
  if (key === 'clienteNombre') return item.cliente?.nombre ?? '';
  if (key === 'usuario') return item.usuario ?? '';
  if (key === 'numeroPedido') return item.numeroPedido ?? 0;
  if (key === 'total') return item.total ?? 0;
  if (key === 'estado') return item.estado;
  if (key === 'fecha') return item.fecha ?? '';
  return '';
}

export default function PedidoTable ({
  pedidos,
  onUpdateEstado,
  onDelete,
  onVer,
}: PedidoTableProps) {
  const { isAdmin } = useAuth();
  const { sortedData, toggleSort, indicator } = useSort(
    pedidos,
    'numeroPedido',
    getPedidoValue
  );

  return (
    <div className='overflow-x-auto rounded-lg border border-slate-200'>
      <table className='min-w-full divide-y divide-slate-200 text-sm'>
        <thead className='bg-slate-50'>
          <tr>
            <th className={thClass} onClick={() => toggleSort('numeroPedido')}>
              Nro Pedido{indicator('numeroPedido')}
            </th>
            <th className={thClass} onClick={() => toggleSort('clienteNombre')}>
              Cliente{indicator('clienteNombre')}
            </th>
            <th className={thClass} onClick={() => toggleSort('usuario')}>
              Usuario{indicator('usuario')}
            </th>
            <th className={thClassRight} onClick={() => toggleSort('total')}>
              Total{indicator('total')}
            </th>
            <th className={thClass} onClick={() => toggleSort('estado')}>
              Estado{indicator('estado')}
            </th>
            <th className={thClass} onClick={() => toggleSort('fecha')}>
              Fecha{indicator('fecha')}
            </th>
            <th className='px-4 py-3 text-right font-medium text-slate-600'>
              Acciones
            </th>
          </tr>
        </thead>
        <tbody className='divide-y divide-slate-200'>
          {sortedData.length === 0
            ? (
              <tr>
                <td colSpan={7} className='px-4 py-8 text-center text-slate-400'>
                  No hay pedidos registrados.
                </td>
              </tr>
              )
            : (
                sortedData.map((pedido) => (
                  <tr key={pedido.id} className='hover:bg-slate-50'>
                    <td className='px-4 py-3 font-medium text-slate-900'>
                      {pedido.numeroPedido ?? '—'}
                    </td>
                    <td className='px-4 py-3 text-slate-600'>
                      {pedido.cliente?.nombre ?? '—'}
                    </td>
                    <td className='px-4 py-3 text-slate-600'>
                      {pedido.usuario ?? '—'}
                    </td>
                    <td className='px-4 py-3 text-slate-600'>
                      {formatCurrency(pedido.total)}
                    </td>
                    <td className='px-4 py-3'>
                      <span
                        className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${
                      estadoColors[pedido.estado] ?? 'text-slate-700 bg-slate-100'
                    }`}
                      >
                        {pedido.estado}
                      </span>
                    </td>
                    <td className='px-4 py-3 text-slate-600'>
                      {formatDate(pedido.fecha)}
                    </td>
                    <td className='px-4 py-3 text-right'>
                      <div className='flex justify-end gap-2'>
                        <Button variant='secondary' onClick={() => onVer(pedido)}>
                          Ver
                        </Button>
                        {isAdmin && (pedido.estado === 'ACEPTADO' || pedido.estado === 'EN_PREPARACION') && (
                          <Button variant='secondary' onClick={() => onUpdateEstado(pedido)}>
                            Estado
                          </Button>
                        )}
                        {isAdmin && (
                          <Button variant='danger' onClick={() => onDelete(pedido)}>
                            Eliminar
                          </Button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))
              )}
        </tbody>
      </table>
    </div>
  );
}
