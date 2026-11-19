// ==============================================================
// TABLA DE PEDIDOS (componente "tonto" / de presentación)
// --------------------------------------------------------------
// Mismo patrón que ObraTable.tsx. Particularidades:
//  - getPedidoValue: función getValue para ordenar columnas cuyo valor no
//    está en la raíz del pedido (ej: "clienteNombre" vive en pedido.cliente).
//  - El botón "Estado" solo aparece si el pedido está ACEPTADO o
//    EN_PREPARACION (esos estados permiten avanzar el flujo).
//  - Formatea fecha y moneda con helpers locales (formatDate/formatCurrency).
//
// FLUJO DE ESTADOS DEL PEDIDO (cadena de valores):
//  RECIBIDO → ACEPTADO → EN_PREPARACION → ENTREGADO
//  (además: CANCELADO y RECHAZADO como estados terminales)
// El botón "Estado" avanza el flujo, por eso solo se habilita en los dos
// estados "avanzables".
// ==============================================================

'use client';

import type { Pedido } from '@/services/pedidoService';
import Button from './Button';
import { useSort } from '@/hooks/useSort';
import { useAuth } from '@/lib/auth/AuthContext';

// Constantes de clases Tailwind para encabezados (izquierda y derecha).
const thClass =
  'px-4 py-3 text-left font-medium text-slate-600 cursor-pointer select-none hover:text-slate-900';
const thClassRight =
  'px-4 py-3 text-right font-medium text-slate-600 cursor-pointer select-none hover:text-slate-900';

// Mapa estado → colores de la "pill". Como los estados los define el
// backend, el `??` en el uso garantiza un color neutro si llega uno nuevo.
const estadoColors: Record<string, string> = {
  RECIBIDO: 'text-yellow-700 bg-yellow-100',
  ACEPTADO: 'text-blue-700 bg-blue-100',
  EN_PREPARACION: 'text-indigo-700 bg-indigo-100',
  ENTREGA_PARCIAL: 'text-purple-700 bg-purple-100',
  ENTREGADO: 'text-green-700 bg-green-100',
  CANCELADO: 'text-red-700 bg-red-100',
  RECHAZADO: 'text-gray-700 bg-gray-100',
};

/**
 * Formatea una fecha ISO (string) a texto legible es-AR con hora.
 *
 * `new Date(iso)` parsea el string ISO y lo convierte a objeto Date;
 * `toLocaleDateString('es-AR', {…})` lo formatea según la región con
 * dígitos fijos. Si el string viene vacío/undefined, devuelve "—".
 *
 * @param {string} [iso] - Fecha ISO 8601 (ej: "2024-10-01T14:30:00.000Z").
 * @returns {string} Fecha formateada (ej: "01/10/2024 11:30") o "—".
 */
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

/**
 * Formatea un número a moneda argentina con 2 decimales.
 *
 * `toLocaleString('es-AR', { minimumFractionDigits: 2 })` agrega
 * separador de miles y fuerza 2 decimales: 10599.5 → "$10.599,50".
 *
 * @param {number} [n] - El monto a formatear (opcional).
 * @returns {string} El monto formateado o "—" si es null/undefined.
 */
function formatCurrency (n?: number) {
  if (n == null) return '—';
  return `$${n.toLocaleString('es-AR', { minimumFractionDigits: 2 })}`;
}

/**
 * Props (contrato) de la tabla de pedidos.
 *
 * @typedef {Object} PedidoTableProps
 * @property {Pedido[]} pedidos - Lista de pedidos a mostrar.
 * @property {(pedido: Pedido) => void} onUpdateEstado - Abre el modal de estado.
 * @property {(pedido: Pedido) => void} onDelete - Confirma y elimina.
 * @property {(pedido: Pedido) => void} onVer - Abre el detalle del pedido.
 * @property {(pedido: Pedido) => void} onEntregar - Abre el remito de entrega.
 */
type PedidoTableProps = {
  pedidos: Pedido[];
  onUpdateEstado: (pedido: Pedido) => void;
  onDelete: (pedido: Pedido) => void;
  onVer: (pedido: Pedido) => void;
  onEntregar: (pedido: Pedido) => void;
  onReintentarReserva?: (pedido: Pedido) => void;
};

/**
 * Función getValue para useSort: traduce cada clave de columna al valor
 * a comparar. Necesaria porque algunos valores NO están en la raíz del
 * pedido (cliente.nombre) o son opcionales (?? con fallback seguro).
 *
 * @param {Pedido} item - El pedido de la fila.
 * @param {string} key - Clave de la columna a ordenar.
 * @returns {string | number} El valor comparable (nunca undefined).
 */
function getPedidoValue (item: Pedido, key: string): string | number {
  if (key === 'clienteNombre') return item.cliente?.nombre ?? '';
  if (key === 'usuario') return item.usuario ?? '';
  if (key === 'numeroPedido') return item.numeroPedido ?? 0;
  if (key === 'total') return item.total ?? 0;
  if (key === 'estado') return item.estado;
  if (key === 'fecha') return item.fecha ?? '';
  return '';
}

/**
 * Tabla de pedidos con ordenamiento por columna.
 *
 * @param {PedidoTableProps} props - Ver definición del contrato.
 * @returns {JSX.Element} El <table> con los pedidos ordenados.
 */
export default function PedidoTable ({
  pedidos,
  onUpdateEstado,
  onDelete,
  onVer,
  onEntregar,
  onReintentarReserva,
}: PedidoTableProps) {
  const { isAdmin } = useAuth();

  // useSort con columna inicial "numeroPedido" y getPedidoValue para
  // saber cómo extraer el valor comparado (incluye campos anidados).
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
                      {/* Pill de estado; `?? 'text-slate-700 bg-slate-100'`
                          protege contra estados desconocidos del backend. */}
                      <span
                        className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${
                      estadoColors[pedido.estado] ?? 'text-slate-700 bg-slate-100'
                    }`}
                      >
                        {pedido.estado}
                      </span>
                      {/* ACEPTADO = reserva de stock pendiente (con stock pasa a EN_PREPARACION). */}
                      {pedido.estado === 'ACEPTADO' && (
                        <span className='ml-2 text-xs text-amber-700'>pendiente de stock</span>
                      )}
                    </td>
                    <td className='px-4 py-3 text-slate-600'>
                      {formatDate(pedido.fecha)}
                    </td>
                    <td className='px-4 py-3 text-right'>
                      <div className='flex justify-end gap-2'>
                        <Button variant='secondary' onClick={() => onVer(pedido)}>
                          Ver
                        </Button>

                        {/* El botón "Estado" SOLO se habilita en ACEPTADO o
                            EN_PREPARACION: en RECIBIDO aún no lo toma el
                            admin y en ENTREGADO/CANCELADO/RECHAZADO el flujo
                            ya terminó (no hay adónde avanzar). */}
                        {isAdmin && (pedido.estado === 'EN_PREPARACION' || pedido.estado === 'ENTREGA_PARCIAL') && (
                          <Button variant='secondary' onClick={() => onEntregar(pedido)}>
                            Entregar
                          </Button>
                        )}
                        {isAdmin && pedido.estado === 'ACEPTADO' && onReintentarReserva && (
                          <Button variant='secondary' onClick={() => onReintentarReserva(pedido)}>
                            Reintentar reserva
                          </Button>
                        )}
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
