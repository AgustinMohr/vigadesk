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
//
// ARQUITECTURA (patrón levantado/levantado de estado — lifting state):
// el form mantiene el estado de CADA campo, pero la PERSISTENCIA (fetch)
// la decide el padre. El form no conoce los services: es reutilizable.
//
// CONCEPTOS CLAVE: inputs controlados, casting `as EstadoObra | ''`,
// validación client-side, `Number()` para convertir strings de forms,
// y `finally` para re-habilitar el botón de guardar.
// ==============================================================

'use client';

import { useState } from 'react';
import type { Obra, EstadoObra } from '@/services/obraService';
import Button from './Button';
import Modal from './Modal';
import { getErrorMessage } from '@/lib/http/api-error';
import Field from './Field';

/**
 * PROPS que recibe del padre (ObrasPageClient).
 *
 * @typedef {Object} ObraFormProps
 * @property {Obra} [obra] - Si existe → edición (campos precargados);
 *   si no → creación (campos vacíos).
 * @property {(data: Partial<Obra>) => Promise<void>} onSubmit - Callback
 *   que recibe los datos del formulario ya tipados con Partial<Obra>.
 * @property {() => void} onCancel - Callback para cerrar sin guardar.
 */
type ObraFormProps = {
  obra?: Obra;
  onSubmit: (data: Partial<Obra>) => Promise<void>;
  onCancel: () => void;
};

/**
 * Modal de formulario para crear o editar una obra.
 *
 * INPUTS CONTROLADOS: cada campo es un useState y vive en el estado de
 * React (no en el DOM). `obra?.direccion ?? ""` precarga el valor al
 * editar; `?? ""` garantiza string siempre (evita el clásico
 * "value undefined" de React al controlar inputs).
 *
 * @param {ObraFormProps} props - Ver ObraFormProps.
 * @returns {JSX.Element} El modal con el formulario de obra.
 */
export default function ObraForm ({ obra, onSubmit, onCancel }: ObraFormProps) {
  // Estado de cada campo. Los números se guardan como STRING porque los
  // inputs type=number devuelven texto; la conversión a Number() ocurre
  // recién en handleSubmit (allá es donde el payload quiere números).
  const [direccion, setDireccion] = useState(obra?.direccion ?? '');
  const [lat, setLat] = useState(obra?.lat?.toString() ?? '');
  const [lng, setLng] = useState(obra?.lng?.toString() ?? '');
  const [presupuesto, setPresupuesto] = useState(
    obra?.presupuesto?.toString() ?? ''
  );
  // El estado es `EstadoObra | ''`: '' representa "sin estado" (la primera
  // opción del <select>). Como <option> siempre devuelve string, el casting
  // en onChange valida que sea un estado válido o el vacío.
  const [estado, setEstado] = useState<EstadoObra | ''>(obra?.estado ?? '');
  const [error, setError] = useState(''); // mensaje de validación
  const [submitting, setSubmitting] = useState(false); // deshabilita botón mientras guarda

  // `!!obra` — doble negación: convierte el objeto a booleano.
  const isEditing = !!obra; // true → modo edición

  /**
   * Se ejecuta al enviar el form (clic en "Guardar" o Enter dentro de un input).
   * `e.preventDefault()` cancela el envío NATIVO del form (recarga de página
   * con GET); en su lugar, validamos y llamamos al callback del padre.
   */
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault(); // evita recargar la página
    setError('');

    // VALIDACIÓN básica en el cliente antes de llamar a la API.
    // No es seguridad (eso lo hace el backend), pero ahorra requests
    // inválidos y da feedback inmediato (mejor UX).
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
      // es un ternario: si el usuario escribió algo → Number (convierte
      // "12.3" → 12.3); si no → undefined (el backend no pisa el valor).
      await onSubmit({
        direccion: direccion.trim(),
        lat: lat.trim() ? Number(lat) : undefined,
        lng: lng.trim() ? Number(lng) : undefined,
        presupuesto: Number(presupuesto),
        estado: estado || undefined, // '' → undefined (si el user no eligió)
      });
    } catch (err) {
      setError(getErrorMessage(err, 'Ocurrió un error al guardar la obra.'));
    } finally {
      setSubmitting(false); // siempre se rehabilita el botón
    }
  };

  return (
    // Overlay: `fixed inset-0` cubre toda la pantalla; `bg-black/40` es un
    // fondo semitransparente (40% de opacidad); flexbox centra la caja.
    <Modal titulo={<>{isEditing ? 'Editar obra' : 'Nueva obra'}</>} onClose={onCancel}>

      <form onSubmit={handleSubmit} className='space-y-4'>
        {/* Cada input = un useState (input controlado) */}
        <Field label='Dirección' required>
          <input
            type='text'
            value={direccion}
            onChange={(e) => setDireccion(e.target.value)}
            className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
          />
        </Field>

        <div className='grid grid-cols-2 gap-3'>
          {/* `step='any'` permite decimales (12.3456), no solo enteros */}
          <Field label='Latitud'>
            <input
              type='number'
              step='any'
              value={lat}
              onChange={(e) => setLat(e.target.value)}
              className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
            />
          </Field>
          <Field label='Longitud'>
            <input
              type='number'
              step='any'
              value={lng}
              onChange={(e) => setLng(e.target.value)}
              className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
            />
          </Field>
        </div>

        <Field label='Presupuesto' required>
          <input
            type='number'
            value={presupuesto}
            onChange={(e) => setPresupuesto(e.target.value)}
            min={100}
            className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
          />
        </Field>

        {/* El <select> también es controlado: `value={estado}` viene del
                estado y cada opción usa la lista de estados como Union Type.
                El casting `as EstadoObra | ''` en onChange es necesario
                porque e.target.value SIEMPRE es string. */}
        <Field label='Estado'>
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
        </Field>

        {/* Render condicional del error: si error es '' (falsy), no se pinta */}
        {error && <p className='text-sm text-red-600'>{error}</p>}

        <div className='flex justify-end gap-2 pt-2'>
          <Button
            variant='secondary'
            onClick={onCancel}
            disabled={submitting}
          >
            Cancelar
          </Button>
          {/* type="submit" dispara el onSubmit del <form> */}
          <Button type='submit' disabled={submitting}>
            {submitting ? 'Guardando…' : 'Guardar'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
