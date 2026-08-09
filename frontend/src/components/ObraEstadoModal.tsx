// ==============================================================
// MODAL DE ESTADO DE OBRA
// Ventana pequeña con un <select> (HABILITADA/PENDIENTE/FINALIZADA)
// que confirma el nuevo estado. Patrón típico de los modales de este
// proyecto: `onSubmit(valor)` avisa al padre y `onCancel` cierra.
// ==============================================================

'use client';

import { useState } from 'react';
import type { EstadoObra } from '@/services/obraService';
import Button from './Button';

// Modal para cambiar el estado de una obra (HABILITADA / PENDIENTE / FINALIZADA)
type ObraEstadoModalProps = {
  obraDireccion: string;
  onSubmit: (estado: EstadoObra) => Promise<void>;
  onCancel: () => void;
};

const estadosPermitidos: EstadoObra[] = [
  'HABILITADA',
  'PENDIENTE',
  'FINALIZADA',
];

export default function ObraEstadoModal ({
  obraDireccion,
  onSubmit,
  onCancel,
}: ObraEstadoModalProps) {
  const [estado, setEstado] = useState<EstadoObra | ''>('');
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
          Cambiar estado
        </h3>

        <p className='mb-3 text-sm text-slate-600'>{obraDireccion}</p>

        <form onSubmit={handleSubmit} className='space-y-4'>
          <div>
            <label className='block text-sm font-medium text-slate-700'>
              Nuevo estado <span className='text-red-500'>*</span>
            </label>
            <select
              value={estado}
              onChange={(e) => setEstado(e.target.value as EstadoObra | '')}
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
