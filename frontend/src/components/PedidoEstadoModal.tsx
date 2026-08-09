// ==============================================================
// MODAL DE ESTADO DE PEDIDO
// Ventana pequeña con un <select> (solo ENTREGADO/CANCELADO) que
// confirma el nuevo estado. Patrón típico de los modales de este
// proyecto: `onSubmit(valor)` avisa al padre y `onCancel` cierra.
// ==============================================================

'use client';

import { useState } from 'react';
import Button from './Button';

// Modal para cambiar el estado de un pedido (ENTREGADO o CANCELADO)
// Solo se abre para pedidos en ACEPTADO o EN_PREPARACION
type PedidoEstadoModalProps = {
  pedidoNumero?: number;
  onSubmit: (estado: string) => Promise<void>;
  onCancel: () => void;
};

const estadosPermitidos = ['ENTREGADO', 'CANCELADO'];

export default function PedidoEstadoModal ({
  pedidoNumero,
  onSubmit,
  onCancel,
}: PedidoEstadoModalProps) {
  const [estado, setEstado] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!estado) {
      setError('Debe seleccionar un estado.');
      return;
    }

    setSubmitting(true);
    try {
      await onSubmit(estado);
    } catch {
      setError('Ocurrió un error al actualizar el estado.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className='fixed inset-0 z-50 flex items-center justify-center bg-black/40'>
      <div className='w-full max-w-sm rounded-lg bg-white p-6 shadow-xl'>
        <h3 className='mb-4 text-lg font-semibold text-slate-900'>
          Actualizar estado
        </h3>

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
