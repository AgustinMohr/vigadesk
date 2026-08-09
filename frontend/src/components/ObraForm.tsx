// ==============================================================
// FORMULARIO DE OBRA (modal de crear/editar)
// --------------------------------------------------------------
// INPUTS CONTROLADOS: cada campo guarda su valor en estado (useState) y
// lo pinta con `value={...}`. Al escribir, `onChange` actualiza el estado
// y React re-renderiza el input con el nuevo valor. Así, el estado SIEMPRE
// refleja lo que el usuario ve (la fuente de verdad es React, no el DOM).
//
// Dos modos según la prop `obra`:
//  - Si llega una obra → modo edición (campos precargados).
//  - Si no llega (undefined) → modo creación (campos vacíos).
// Al guardar, avisa al padre con `onSubmit(data)`; el padre llama al API.
// ==============================================================

'use client';

import { useState } from 'react';
import type { Obra, EstadoObra } from '@/services/obraService';
import Button from './Button';

// PROPS que recibe del padre (ObrasPageClient):
//  - obra?: si existe → edición; si no → creación.
//  - onSubmit: callback que recibe los datos del formulario.
//  - onCancel: callback para cerrar sin guardar.
type ObraFormProps = {
  obra?: Obra;
  onSubmit: (data: Partial<Obra>) => Promise<void>;
  onCancel: () => void;
};

export default function ObraForm ({ obra, onSubmit, onCancel }: ObraFormProps) {
  // Estado de cada campo. `obra?.direccion ?? ""` precarga el valor al editar.
  const [direccion, setDireccion] = useState(obra?.direccion ?? '');
  const [lat, setLat] = useState(obra?.lat?.toString() ?? '');
  const [lng, setLng] = useState(obra?.lng?.toString() ?? '');
  const [presupuesto, setPresupuesto] = useState(
    obra?.presupuesto?.toString() ?? ''
  );
  const [estado, setEstado] = useState<EstadoObra | ''>(obra?.estado ?? '');
  const [error, setError] = useState(''); // mensaje de validación
  const [submitting, setSubmitting] = useState(false); // deshabilita botón mientras guarda

  const isEditing = !!obra; // true → modo edición

  // Se ejecuta al enviar el form (clic en "Guardar" o Enter).
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault(); // evita recargar la página
    setError('');

    // VALIDACIÓN básica en el cliente antes de llamar a la API
    if (!direccion.trim()) {
      setError('La dirección es obligatoria.');
      return;
    }
    if (!presupuesto.trim() || Number(presupuesto) < 100) {
      setError('El presupuesto debe ser al menos 100.');
      return;
    }

    setSubmitting(true);
    try {
      // Arma el objeto a enviar. `lat.trim() ? Number(lat) : undefined`
      // convierte a número solo si el usuario escribió algo.
      await onSubmit({
        direccion: direccion.trim(),
        lat: lat.trim() ? Number(lat) : undefined,
        lng: lng.trim() ? Number(lng) : undefined,
        presupuesto: Number(presupuesto),
        estado: estado || undefined,
      });
    } catch {
      setError('Ocurrió un error al guardar la obra.');
    } finally {
      setSubmitting(false); // siempre se rehabilita el botón
    }
  };

  return (
    // Fondo oscuro que cubre la pantalla + caja central blanca
    <div className='fixed inset-0 z-50 flex items-center justify-center bg-black/40'>
      <div className='w-full max-w-md rounded-lg bg-white p-6 shadow-xl'>
        <h3 className='mb-4 text-lg font-semibold text-slate-900'>
          {isEditing ? 'Editar obra' : 'Nueva obra'}
        </h3>

        <form onSubmit={handleSubmit} className='space-y-4'>
          <div>
            <label className='block text-sm font-medium text-slate-700'>
              Dirección <span className='text-red-500'>*</span>
            </label>
            {/* Cada input = un useState (input controlado) */}
            <input
              type='text'
              value={direccion}
              onChange={(e) => setDireccion(e.target.value)}
              className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
            />
          </div>

          <div className='grid grid-cols-2 gap-3'>
            <div>
              <label className='block text-sm font-medium text-slate-700'>
                Latitud
              </label>
              <input
                type='number'
                step='any'
                value={lat}
                onChange={(e) => setLat(e.target.value)}
                className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
              />
            </div>
            <div>
              <label className='block text-sm font-medium text-slate-700'>
                Longitud
              </label>
              <input
                type='number'
                step='any'
                value={lng}
                onChange={(e) => setLng(e.target.value)}
                className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
              />
            </div>
          </div>

          <div>
            <label className='block text-sm font-medium text-slate-700'>
              Presupuesto <span className='text-red-500'>*</span>
            </label>
            <input
              type='number'
              value={presupuesto}
              onChange={(e) => setPresupuesto(e.target.value)}
              min={100}
              className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
            />
          </div>

          <div>
            <label className='block text-sm font-medium text-slate-700'>
              Estado
            </label>
            {/* Select también controlado: el valor sale del estado */}
            <select
              value={estado}
              onChange={(e) => setEstado(e.target.value as EstadoObra | '')}
              className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
            >
              <option value=''>Sin estado</option>
              {(['HABILITADA', 'PENDIENTE', 'FINALIZADA'] as EstadoObra[]).map(
                (e) => (
                  <option key={e} value={e}>
                    {e}
                  </option>
                )
              )}
            </select>
          </div>

          {/* Render condicional del error */}
          {error && <p className='text-sm text-red-600'>{error}</p>}

          <div className='flex justify-end gap-2 pt-2'>
            <Button
              variant='secondary'
              onClick={onCancel}
              disabled={submitting}
            >
              Cancelar
            </Button>
            {/* type="submit" dispara onSubmit del <form> */}
            <Button type='submit' disabled={submitting}>
              {submitting ? 'Guardando…' : 'Guardar'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
