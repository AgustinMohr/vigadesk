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
import { http } from '@/services/http';
import Button from './Button';
import Modal from './Modal';
import { useFeedback } from './Feedback';
import { claseDeEstadoPedido } from '@/lib/domain/pedido';

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
 * Nota sobre los totales: el modal SOLO muestra, no recalcula. El backend
 * (ms-pedidos) guarda por línea `precioUnitario` (precio de lista), el
 * `descuento` (porcentaje aplicado) y `precioFinal` (el neto ya con el
 * descuento). El "Subtotal" de cada fila es ese neto, y `pedido.total` es
 * la suma de los netos → la tabla y el total siempre cierran.
 *
 * @param {PedidoDetalleModalProps} props - Ver PedidoDetalleModalProps.
 * @returns {JSX.Element} El modal de detalle del pedido.
 */
export default function PedidoDetalleModal ({
  pedido,
  onClose,
}: PedidoDetalleModalProps) {
  const { avisar } = useFeedback();
  return (
    // Overlay de modal: fondo oscuro + caja blanca centrada (ancha, tiene
    // tabla de productos con varias columnas).
    <Modal titulo={<>Detalle del pedido #{pedido.numeroPedido}</>} onClose={onClose} ancho='max-w-2xl' mostrarCerrar>

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
                claseDeEstadoPedido(pedido.estado)
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
              <th className='px-3 py-2 text-right font-medium text-slate-600'>Pedida</th>
              <th className='px-3 py-2 text-right font-medium text-slate-600'>Entregada</th>
              <th className='px-3 py-2 text-right font-medium text-slate-600'>Pendiente</th>
              <th className='px-3 py-2 text-right font-medium text-slate-600'>P. Unitario</th>
              <th className='px-3 py-2 text-right font-medium text-slate-600'>Descuento</th>
              <th className='px-3 py-2 text-right font-medium text-slate-600'>Subtotal</th>
            </tr>
          </thead>
          <tbody className='divide-y divide-slate-200'>
            {pedido.detalle.length === 0
              ? (
                <tr>
                  <td colSpan={7} className='px-3 py-6 text-center text-slate-500'>
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
                        <span className='ml-1 text-xs text-slate-500'>
                          ({item.producto.unidadMedida ?? 'UNIDAD'})
                        </span>
                      </td>
                      <td className='px-3 py-2 text-right text-slate-600'>{item.cantidad}</td>
                      <td className='px-3 py-2 text-right text-slate-600'>
                        {item.cantidadEntregada ?? 0}
                      </td>
                      <td className='px-3 py-2 text-right text-slate-600'>
                        {item.cantidadPendiente ?? item.cantidad}
                      </td>
                      {/* Cadena de fallbacks con ?? para el precio unitario */}
                      <td className='px-3 py-2 text-right text-slate-600'>
                        {formatCurrency(item.precioUnitario ?? item.producto.precio ?? 0)}
                      </td>
                      {/* Descuento: PORCENTAJE aplicado a la línea (lo que
                            guardó el backend como `descuento`). Solo se pinta
                            si existe y es > 0. */}
                      <td className='px-3 py-2 text-right text-slate-600'>
                        {item.descuento && item.descuento > 0
                          ? `${Number(item.descuento).toLocaleString('es-AR')}%`
                          : '—'}
                      </td>
                      {/* Subtotal mostrado: el NETO de la línea (precioFinal
                            que calculó el backend con el descuento). Si el
                            backend no lo trajo, cae al bruto precio × cantidad. */}
                      <td className='px-3 py-2 text-right font-medium text-slate-900'>
                        {item.precioFinal != null
                          ? formatCurrency(item.precioFinal)
                          : formatCurrency((item.precioUnitario ?? item.producto.precio ?? 0) * item.cantidad)}
                      </td>
                    </tr>
                  ))
                )}
          </tbody>
        </table>
      </div>

      {pedido.entregas && pedido.entregas.length > 0 && (
        <div className='mt-6'>
          <h4 className='mb-2 text-sm font-semibold text-slate-700'>
            Remitos registrados
          </h4>
          <div className='space-y-2'>
            {pedido.entregas.map((entrega) => (
              <div key={entrega.id} className='rounded-md bg-slate-50 px-3 py-2 text-sm'>
                <div className='flex justify-between gap-2'>
                  <span className='font-medium text-slate-900'>
                    Remito #{entrega.numeroRemito}
                  </span>
                  <span className='text-slate-500'>{formatDate(entrega.fecha)}</span>
                </div>
                <p className='text-slate-600'>Receptor: {entrega.receptor}</p>
                <p className='text-xs text-slate-500'>
                  {entrega.items.map((item) => `${item.productoNombre}: ${item.cantidad}`).join(' · ')}
                </p>
                <button
                  type='button'
                  onClick={() => {
                    http.download(
                        `/api/pedidos/${pedido.id}/entregas/${entrega.id}/pdf`,
                        `remito-${entrega.numeroRemito}.pdf`
                    ).catch(() => avisar('error', 'No se pudo descargar el remito.'));
                  }}
                  className='mt-1 text-xs font-medium text-slate-600 underline underline-offset-2 hover:text-slate-900'
                >
                  Descargar remito PDF
                </button>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className='mt-6 flex justify-end gap-2'>
        <Button
          variant='secondary'
          onClick={() => {
            http.download(
                `/api/pedidos/${pedido.id}/pdf`,
                `pedido-${pedido.numeroPedido}.pdf`
            ).catch(() => avisar('error', 'No se pudo descargar el pedido.'));
          }}
        >
          PDF pedido
        </Button>
        <Button variant='secondary' onClick={onClose}>
          Cerrar
        </Button>
      </div>
    </Modal>
  );
}
