// ==============================================================
// MODAL DE DESCUENTO PROMOCIONAL
// Ventana pequeña con un solo input numérico. Valida que el valor sea
// >= 0 y llama onSubmit(productoId, descuento) para persistir.
// ==============================================================

'use client';

import { useState } from 'react';
import type { Producto } from '@/services/productoService';
import Button from './Button';

type DescuentoModalProps = {
  producto: Producto;
  onSubmit: (id: number, descuentoPromocional: number) => Promise<void>;
  onCancel: () => void;
};

export default function DescuentoModal ({
  producto,
  onSubmit,
  onCancel,
}: DescuentoModalProps) {
  const [valor, setValor] = useState(
    producto.descuentoPromocional?.toString() ?? '0'
  );
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    const descuento = Number(valor);
    if (Number.isNaN(descuento) || descuento < 0) {
      setError('Ingrese un valor numérico válido (mayor o igual a 0).');
      return;
    }

    setSubmitting(true);
    try {
      await onSubmit(producto.id!, descuento);
    } catch {
      setError('Ocurrió un error al aplicar el descuento.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className='fixed inset-0 z-50 flex items-center justify-center bg-black/40'>
      <div className='w-full max-w-sm rounded-lg bg-white p-6 shadow-xl'>
        <h3 className='mb-4 text-lg font-semibold text-slate-900'>
          Aplicar descuento promocional
        </h3>

        <p className='mb-4 text-sm text-slate-600'>
          Producto: <span className='font-medium text-slate-900'>{producto.nombre}</span>
        </p>

        <form onSubmit={handleSubmit} className='space-y-4'>
          <div>
            <label className='block text-sm font-medium text-slate-700'>
              Descuento promocional ($)
            </label>
            <input
              type='number'
              step='0.01'
              min='0'
              value={valor}
              onChange={(e) => setValor(e.target.value)}
              className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
            />
          </div>

          {error && (
            <p className='text-sm text-red-600'>{error}</p>
          )}

          <div className='flex justify-end gap-2 pt-2'>
            <Button variant='secondary' onClick={onCancel} disabled={submitting}>
              Cancelar
            </Button>
            <Button type='submit' disabled={submitting}>
              {submitting ? 'Aplicando…' : 'Aplicar'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
