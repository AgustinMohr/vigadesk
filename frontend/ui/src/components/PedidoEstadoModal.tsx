// ==============================================================
// MODAL DE ESTADO DE PEDIDO
// --------------------------------------------------------------
// Ventana pequeña con un <select> (CANCELADO) que confirma el nuevo
// estado. La entrega se registra desde el modal de remitos, no acá.
//
// LÓGICA DE NEGOCIO: este modal se abre para pedidos en ACEPTADO o
// EN_PREPARACION (lo decide PedidoTable). Acá solo se cancela; las
// cantidades entregadas se registran con EntregaModal.
// ==============================================================

'use client';

import { useState } from 'react';
import Button from './Button';
import { getErrorMessage } from '@/lib/http/api-error';

/**
 * PROPS del modal de estado de pedido.
 *
 * @typedef {Object} PedidoEstadoModalProps
 * @property {number} [pedidoNumero] - Contexto visual: el número del pedido
 *   (opcional; solo para mostrar "Pedido N° 12").
 * @property {(estado: string) => Promise<void>} onSubmit - Callback con el
 *   nuevo estado elegido; el padre persiste (pedidoService.updateEstado).
 * @property {() => void} onCancel - Cierra el modal sin cambiar nada.
 */
type PedidoEstadoModalProps = {
  pedidoNumero?: number;
  onSubmit: (estado: string) => Promise<void>;
  onCancel: () => void;
};

// Estados de cierre permitidos desde el modal de un pedido en curso.
const estadosPermitidos = ['CANCELADO'];

/**
 * Modal para cancelar un pedido en curso.
 *
 * Componente "dumb": valida la selección y delega el valor al padre.
 *
 * @param {PedidoEstadoModalProps} props - Ver PedidoEstadoModalProps.
 * @returns {JSX.Element} El modal con el select de estados.
 */
export default function PedidoEstadoModal ({
  pedidoNumero,
  onSubmit,
  onCancel,
}: PedidoEstadoModalProps) {
  // Estado como string plano: '' = placeholder, luego 'ENTREGADO'/'CANCELADO'.
  const [estado, setEstado] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  /**
   * Valida la selección y delega la actualización al padre.
   */
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    // '' (placeholder) no es un estado válido → error.
    if (!estado) {
      setError('Debe seleccionar un estado.');
      return;
    }

    setSubmitting(true);
    try {
      await onSubmit(estado);
    } catch (err) {
      setError(getErrorMessage(err, 'Ocurrió un error al actualizar el estado.'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    // Overlay de modal: fondo oscuro + caja blanca centrada (angosta).
    <div className='fixed inset-0 z-50 flex items-center justify-center bg-black/40'>
      <div className='w-full max-w-sm rounded-lg bg-white p-6 shadow-xl'>
        <h3 className='mb-4 text-lg font-semibold text-slate-900'>
          Actualizar estado
        </h3>

        {/* Contexto: número de pedido si el padre lo proveyó */}
        {pedidoNumero && (
          <p className='mb-3 text-sm text-slate-600'>
            Pedido N° {pedidoNumero}
          </p>
        )}

        <form onSubmit={handleSubmit} className='space-y-4'>
          <div>
            <label className='block text-sm font-medium text-slate-700'>
              Nuevo estado <span className='text-red-500'>*</span>
            </label>
            <select
              value={estado}
              onChange={(e) => setEstado(e.target.value)}
              className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
            >
              <option value=''>Seleccionar estado</option>
              {estadosPermitidos.map((e) => (
                <option key={e} value={e}>
                  {e}
                </option>
              ))}
            </select>
          </div>

          {error && <p className='text-sm text-red-600'>{error}</p>}

          <div className='flex justify-end gap-2 pt-2'>
            <Button variant='secondary' onClick={onCancel} disabled={submitting}>
              Cancelar
            </Button>
            <Button type='submit' disabled={submitting}>
              {submitting ? 'Actualizando…' : 'Actualizar'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
