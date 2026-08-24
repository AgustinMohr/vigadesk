// ==============================================================
// MODAL DE DETALLE DE PEDIDO
// --------------------------------------------------------------
// Ventana de SOLO LECTURA: muestra el pedido completo (cliente, obra,
// fecha, estado, total y la tabla de productos con subtotales).
// Particularidad: helpers locales formatDate/formatCurrency para darle
// formato legible a los datos crudos (una fecha ISO o un número).
//
// COMPONENTE SIN ESTADO (stateless/presentacional puro): no usa ningún
// hook React (nada de useState/useEffect/useContext). Recibe el pedido
// por props y renderiza — el fetch lo hizo el padre. Es el caso más
// "tonto" de componente posible: funciona SOLO con lo que le dan.
// ==============================================================

'use client';

import type { Pedido } from '@/services/pedidoService';
import Button from './Button';

/**
 * PROPS del modal de detalle de pedido.
 *
 * @typedef {Object} PedidoDetalleModalProps
 * @property {Pedido} pedido - El pedido completo a mostrar.
 * @property {() => void} onClose - Cierra el modal.
 */
type PedidoDetalleModalProps = {
  pedido: Pedido;
  onClose: () => void;
};

// Mapa estado → colores de la pill (misma paleta que PedidoTable).
const estadoColors: Record<string, string> = {
  RECIBIDO: 'text-yellow-700 bg-yellow-100',
  ACEPTADO: 'text-blue-700 bg-blue-100',
  EN_PREPARACION: 'text-indigo-700 bg-indigo-100',
  ENTREGADO: 'text-green-700 bg-green-100',
  CANCELADO: 'text-red-700 bg-red-100',
  RECHAZADO: 'text-gray-700 bg-gray-100',
};

/**
 * Formatea una fecha ISO (string) a texto legible es-AR con hora.
 *
 * @param {string} [iso] - Fecha ISO 8601 (ej: "2024-10-01T14:30:00.000Z").
 * @returns {string} Fecha formateada o "—" si el valor está vacío.
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
 * @param {number} [n] - El monto a formatear.
 * @returns {string} El monto formateado o "—" si es null/undefined.
 */
function formatCurrency (n?: number) {
  if (n == null) return '—';
  return `$${n.toLocaleString('es-AR', { minimumFractionDigits: 2 })}`;
}

/**
 * Modal de solo lectura con el detalle completo de un pedido.
 *
 * Nota sobre los subtotales: usa `(precioUnitario ?? producto.precio ?? 0)`
 * — el `??` (nullish) encadena fallbacks: si el backend trajo precioUnitario
 * (después de aplicar descuento), lo usa; si no, el precio base del
 * producto; si no hay nada, 0. El modal SOLO muestra: no recalcula.
 *
 * @param {PedidoDetalleModalProps} props - Ver PedidoDetalleModalProps.
 * @returns {JSX.Element} El modal de detalle del pedido.
 */
export default function PedidoDetalleModal ({
  pedido,
  onClose,
}: PedidoDetalleModalProps) {
  return (
    // Overlay de modal: fondo oscuro + caja blanca centrada (ancha, tiene
    // tabla de productos con varias columnas).
    <div className='fixed inset-0 z-50 flex items-center justify-center bg-black/40'>
      <div className='w-full max-w-2xl rounded-lg bg-white p-6 shadow-xl'>
        <div className='mb-4 flex items-center justify-between'>
          <h3 className='text-lg font-semibold text-slate-900'>
            Detalle del pedido #{pedido.numeroPedido}
          </h3>
          <button
            onClick={onClose}
            className='text-slate-400 hover:text-slate-600 text-xl leading-none'
          >
            &times;
          </button>
        </div>

        {/* Grilla de datos del pedido (2 columnas). `col-span-2` hace que
            Observaciones y Total ocupen el ancho completo de la grilla. */}
        <div className='grid grid-cols-2 gap-4 text-sm'>
          <div>
            <span className='font-medium text-slate-500'>Cliente:</span>
            <p className='text-slate-900'>{pedido.cliente.nombre}</p>
          </div>
          <div>
            <span className='font-medium text-slate-500'>Obra:</span>
            {/* La obra es opcional (un pedido puede no tener obra) */}
            <p className='text-slate-900'>{pedido.obra?.direccion ?? '—'}</p>
          </div>
          <div>
            <span className='font-medium text-slate-500'>Fecha:</span>
            <p className='text-slate-900'>{formatDate(pedido.fecha)}</p>
          </div>
          <div>
            <span className='font-medium text-slate-500'>Estado:</span>
            {/* Pill con color según el estado; fallback para estados nuevos */}
            <span
              className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium mt-0.5 ${
                estadoColors[pedido.estado] ?? 'text-slate-700 bg-slate-100'
              }`}
            >
              {pedido.estado}
            </span>
          </div>
          <div className='col-span-2'>
            <span className='font-medium text-slate-500'>Observaciones:</span>
            <p className='text-slate-900'>{pedido.observaciones ?? '—'}</p>
          </div>
          <div className='col-span-2'>
            <span className='font-medium text-slate-500'>Total:</span>
            <p className='text-lg font-semibold text-slate-900'>
              {formatCurrency(pedido.total)}
            </p>
          </div>
        </div>

        <hr className='my-4 border-slate-200' />

        <h4 className='mb-3 text-sm font-semibold text-slate-700'>
          Detalle del pedido
        </h4>

        {/* Tabla de líneas del pedido (una fila por producto) */}
        <div className='overflow-x-auto rounded-lg border border-slate-200'>
          <table className='min-w-full divide-y divide-slate-200 text-sm'>
            <thead className='bg-slate-50'>
              <tr>
                <th className='px-3 py-2 text-left font-medium text-slate-600'>Producto</th>
                <th className='px-3 py-2 text-right font-medium text-slate-600'>Cantidad</th>
                <th className='px-3 py-2 text-right font-medium text-slate-600'>P. Unitario</th>
                <th className='px-3 py-2 text-right font-medium text-slate-600'>Descuento</th>
                <th className='px-3 py-2 text-right font-medium text-slate-600'>Subtotal</th>
              </tr>
            </thead>
            <tbody className='divide-y divide-slate-200'>
              {pedido.detalle.length === 0
                ? (
                  <tr>
                    <td colSpan={5} className='px-3 py-6 text-center text-slate-400'>
                      El pedido no tiene productos.
                    </td>
                  </tr>
                  )
                : (
                    pedido.detalle.map((item, idx) => (
                      // `key={idx}`: índice como key. Aceptable acá porque es
                      // una lista estática de solo lectura (no se reordena).
                      <tr key={idx} className='hover:bg-slate-50'>
                        <td className='px-3 py-2 text-slate-900'>
                          {/* El nombre del producto puede faltar en la info
                              mínima → fallback "Producto #id" */}
                          {item.producto.nombre ?? `Producto #${item.producto.id}`}
                        </td>
                        <td className='px-3 py-2 text-right text-slate-600'>{item.cantidad}</td>
                        {/* Cadena de fallbacks con ?? para el precio unitario */}
                        <td className='px-3 py-2 text-right text-slate-600'>
                          {formatCurrency(item.precioUnitario ?? item.producto.precio ?? 0)}
                        </td>
                        {/* Descuento: solo se pinta si existe y es > 0 */}
                        <td className='px-3 py-2 text-right text-slate-600'>
                          {item.descuento && item.descuento > 0
                            ? formatCurrency(item.descuento)
                            : '—'}
                        </td>
                        {/* Subtotal mostrado: precio unitario × cantidad */}
                        <td className='px-3 py-2 text-right font-medium text-slate-900'>
                          {formatCurrency((item.precioUnitario ?? item.producto.precio ?? 0) * item.cantidad)}
                        </td>
                      </tr>
                    ))
                  )}
            </tbody>
          </table>
        </div>

        <div className='mt-6 flex justify-end'>
          <Button variant='secondary' onClick={onClose}>
            Cerrar
          </Button>
        </div>
      </div>
    </div>
  );
}
