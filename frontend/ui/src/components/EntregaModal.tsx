'use client';

import { useState } from 'react';
import type { EntregaInput, Pedido } from '@/services/pedidoService';
import Button from './Button';
import Modal from './Modal';
import { getErrorMessage } from '@/lib/http/api-error';
import Field from './Field';

type EntregaModalProps = {
  pedido: Pedido;
  onSubmit: (entrega: EntregaInput) => Promise<void>;
  onCancel: () => void;
};

export default function EntregaModal ({ pedido, onSubmit, onCancel }: EntregaModalProps) {
  const lineas = pedido.detalle.filter((item) => (item.cantidadPendiente ?? item.cantidad) > 0);
  const [cantidades, setCantidades] = useState<Record<string, string>>(() =>
    Object.fromEntries(
      lineas.map((item) => [
        item.lineaId,
        String(item.cantidadPendiente ?? item.cantidad),
      ])
    )
  );
  const [receptor, setReceptor] = useState('');
  const [observaciones, setObservaciones] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!receptor.trim()) {
      setError('El receptor es obligatorio.');
      return;
    }

    const items = lineas
      .map((item) => ({
        lineaId: item.lineaId,
        cantidad: Number(cantidades[item.lineaId] ?? 0),
      }))
      .filter((item) => item.cantidad > 0);

    if (items.length === 0) {
      setError('Ingrese al menos una cantidad entregada.');
      return;
    }

    for (const item of items) {
      const linea = lineas.find((line) => line.lineaId === item.lineaId);
      const pendiente = linea?.cantidadPendiente ?? linea?.cantidad ?? 0;
      if (item.cantidad > pendiente) {
        setError(`La cantidad de ${linea?.producto.nombre ?? 'un producto'} supera la pendiente.`);
        return;
      }
    }

    setSubmitting(true);
    try {
      await onSubmit({
        receptor: receptor.trim(),
        observaciones: observaciones.trim() || undefined,
        items,
      });
    } catch (err) {
      setError(getErrorMessage(err, 'Ocurrió un error al registrar la entrega.'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal titulo={<>Registrar entrega · Pedido #{pedido.numeroPedido}</>} onClose={onCancel} ancho='max-w-xl' className='max-h-[90vh] overflow-y-auto'>

      <form onSubmit={handleSubmit} className='space-y-4'>
        <div className='grid grid-cols-2 gap-4'>
          <Field label='Receptor en obra' required>
            <input
              type='text'
              value={receptor}
              onChange={(e) => setReceptor(e.target.value)}
              className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
            />
          </Field>
          <Field label='Observaciones'>
            <input
              type='text'
              value={observaciones}
              onChange={(e) => setObservaciones(e.target.value)}
              className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
            />
          </Field>
        </div>

        <div className='space-y-2'>
          <p className='text-sm font-medium text-slate-700'>Materiales entregados</p>
          {lineas.map((item) => {
            const pendiente = item.cantidadPendiente ?? item.cantidad;
            return (
              <div key={item.lineaId} className='flex items-end gap-3 rounded-md bg-slate-50 px-3 py-2'>
                <div className='flex-1'>
                  <p className='text-sm font-medium text-slate-900'>
                    {item.producto.nombre ?? `Producto #${item.producto.id}`}
                  </p>
                  <p className='text-xs text-slate-500'>
                    Pendiente: {pendiente} {item.producto.unidadMedida ?? 'UNIDAD'}
                  </p>
                </div>
                <Field label='Entregar' labelClassName='block text-xs text-slate-500'>
                  <input
                    type='number'
                    min={0}
                    max={pendiente}
                    step={0.001}
                    value={cantidades[item.lineaId] ?? ''}
                    onChange={(e) => setCantidades({
                      ...cantidades,
                      [item.lineaId]: e.target.value,
                    })}
                    className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
                  />
                </Field>
              </div>
            );
          })}
        </div>

        {error && <p className='text-sm text-red-600'>{error}</p>}

        <div className='flex justify-end gap-2 pt-2'>
          <Button variant='secondary' onClick={onCancel} disabled={submitting}>
            Cancelar
          </Button>
          <Button type='submit' disabled={submitting}>
            {submitting ? 'Registrando…' : 'Registrar entrega'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
