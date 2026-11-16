// ==============================================================
// MODAL DE ESTADO DE OBRA
// --------------------------------------------------------------
// Ventana pequeña con un <select> (HABILITADA / PENDIENTE / FINALIZADA)
// que confirma el nuevo estado. Patrón típico de los modales de este
// proyecto: `onSubmit(valor)` avisa al padre y `onCancel` cierra.
//
// POR QUÉ LA LISTA ES UNA CONSTANTE EXTERNA: `estadosPermitidos` vive
// FUERA del componente (al nivel del módulo). Así no se recrea en cada
// render (si estuviera adentro, `estadosPermitidos.map` recibiría un
// array nuevo por render — inútil, y además rompería memoizaciones).
// Es "data fija", no estado.
// ==============================================================

'use client';

import { useState } from 'react';
import type { EstadoObra } from '@/services/obraService';
import Button from './Button';
import { getErrorMessage } from '@/lib/http/api-error';

/**
 * PROPS del modal de estado de obra.
 *
 * @typedef {Object} ObraEstadoModalProps
 * @property {string} obraDireccion - Contexto visual: la dirección de la obra
 *   cuyo estado se está cambiando (el id NO se pasa: lo resuelve el padre).
 * @property {(estado: EstadoObra) => Promise<void>} onSubmit - Callback con
 *   el nuevo estado elegido; el padre persiste (obraService.cambiarEstado).
 * @property {() => void} onCancel - Cierra el modal sin cambiar nada.
 */
type ObraEstadoModalProps = {
  obraDireccion: string;
  onSubmit: (estado: EstadoObra) => Promise<void>;
  onCancel: () => void;
};

// Estados válidos del ciclo de vida de una obra (Union Type del service).
const estadosPermitidos: EstadoObra[] = [
  'HABILITADA',
  'PENDIENTE',
  'FINALIZADA',
];

/**
 * Modal para cambiar el estado de una obra.
 *
 * Es un componente "dumb" (solo vista + validación): el select es el
 * estado y al confirmar delega el valor al callback del padre.
 *
 * @param {ObraEstadoModalProps} props - Ver ObraEstadoModalProps.
 * @returns {JSX.Element} El modal con el select de estados.
 */
export default function ObraEstadoModal ({
  obraDireccion,
  onSubmit,
  onCancel,
}: ObraEstadoModalProps) {
  // `EstadoObra | ''`: el valor '' representa la opción "Seleccionar estado"
  // (placeholder). El casting en onChange valida que lo elegido sea válido.
  const [estado, setEstado] = useState<EstadoObra | ''>('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  /**
   * Valida la selección y delega la actualización al padre.
   */
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    // Si sigue en '' (placeholder), no hay estado válido → error.
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
    // Overlay de modal: fondo oscuro + caja blanca centrada (angosta,
    // solo trae un select).
    <div className='fixed inset-0 z-50 flex items-center justify-center bg-black/40'>
      <div className='w-full max-w-sm rounded-lg bg-white p-6 shadow-xl'>
        <h3 className='mb-4 text-lg font-semibold text-slate-900'>
          Cambiar estado
        </h3>

        {/* Contexto de la acción */}
        <p className='mb-3 text-sm text-slate-600'>{obraDireccion}</p>

        <form onSubmit={handleSubmit} className='space-y-4'>
          <div>
            <label className='block text-sm font-medium text-slate-700'>
              Nuevo estado <span className='text-red-500'>*</span>
            </label>
            {/* Select controlado por estado */}
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
