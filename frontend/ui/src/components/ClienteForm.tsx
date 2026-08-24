// ==============================================================
// FORMULARIO DE CLIENTE (modal crear/editar)
// --------------------------------------------------------------
// Mismo patrón que ObraForm.tsx (ver ahí la teoría de INPUTS CONTROLADOS
// y del doble modo crear/editar). Particularidades:
//  - Gestión de tipos: los inputs de tipo number manejan el valor como
//    STRING en el estado y lo convierten a Número recién al enviar.
//  - Campos opcionales (cuit, maximoDescubierto) se mandan como
//    `undefined` si quedaron vacíos → el backend no los pisa al editar.
//  - Validación previa al envío con mensaje de error en el propio modal.
// ==============================================================

'use client';

import { useState } from 'react';
import type { Cliente } from '@/services/clienteService';
import Button from './Button';

/**
 * Forma de datos que el form devuelve al padre al guardar.
 * `Partial<Cliente>` = solo los campos editables; el id lo maneja el padre.
 *
 * @typedef {Object} ClienteFormProps
 * @property {Cliente} [cliente] - Si existe → edición (campos precargados);
 *   si es undefined → creación (campos vacíos).
 * @property {(data: Partial<Cliente>) => Promise<void>} onSubmit - Callback
 *   que recibe los datos tipados; el padre llama a la API y cierra el modal.
 * @property {() => void} onCancel - Callback para cerrar el modal sin guardar.
 */
type ClienteFormProps = {
  cliente?: Cliente;          // Si existe → edición, si no → creación
  onSubmit: (data: Partial<Cliente>) => Promise<void>;
  onCancel: () => void;
};

/**
 * Modal de formulario para crear o editar un cliente.
 *
 * INPUTS CONTROLADOS: cada campo guarda su valor en useState y se pinta
 * con `value={...}`. Al escribir, onChange actualiza el estado y React
 * re-renderiza el input: la fuente de verdad ES el estado, no el DOM.
 *
 * @param {ClienteFormProps} props - Ver ClienteFormProps.
 * @returns {JSX.Element} El modal con el formulario.
 */
export default function ClienteForm ({
  cliente,
  onSubmit,
  onCancel,
}: ClienteFormProps) {
  // Nota: `?.toString() ?? ''` — si cliente existe, convierte el número a
  // string (los inputs type=number trabajan con strings); si no existe o
  // el campo es opcional, usa '' (campo vacío).
  const [nombre, setNombre] = useState(cliente?.nombre ?? '');
  const [correoElectronico, setCorreoElectronico] = useState(
    cliente?.correoElectronico ?? ''
  );
  const [cuit, setCuit] = useState(cliente?.cuit ?? '');
  const [maximoDescubierto, setMaximoDescubierto] = useState(
    cliente?.maximoDescubierto?.toString() ?? ''
  );
  const [maxObrasActivas, setMaxObrasActivas] = useState(
    cliente?.maxObrasActivas?.toString() ?? ''
  );
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // `!!cliente` (doble negación) convierte el objeto o undefined en
  // booleano: true si existe, false si no.
  const isEditing = !!cliente;

  /**
   * Maneja el submit: valida, arma el payload y delega al padre.
   * `e.preventDefault()` evita que el <form> haga la recarga clásica de
   * página de la web antigua (request GET al mismo URL).
   */
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    // VALIDACIÓN en el cliente antes de tocar la API (UX + menos errores):
    // `.trim()` elimina espacios a los costados → "  a  " == "a".
    if (!nombre.trim() || !correoElectronico.trim()) {
      setError('Nombre y correo electrónico son obligatorios.');
      return;
    }
    // Regla de negocio: mínimo 1 obra activa por cliente.
    if (!maxObrasActivas.trim() || Number(maxObrasActivas) < 1) {
      setError('Las obras activas máximas deben ser al menos 1.');
      return;
    }

    setSubmitting(true); // deshabilita botones mientras guarda (evita doble submit)
    try {
      await onSubmit({
        // Los campos requeridos se mandan limpios (con trim).
        nombre: nombre.trim(),
        correoElectronico: correoElectronico.trim(),
        // `cuit.trim() || undefined`: si quedó vacío → undefined → el
        // backend no lo incluye / no lo pisa (común en edición).
        cuit: cuit.trim() || undefined,
        // Idem: solo se convierte a Number si el usuario escribió algo.
        maximoDescubierto: maximoDescubierto
          ? Number(maximoDescubierto)
          : undefined,
        maxObrasActivas: Number(maxObrasActivas),
      });
    } catch {
      setError('Ocurrió un error al guardar el cliente.');
    } finally {
      // `finally` SIEMPRE se ejecuta (con éxito o error) → el botón se
      // rehabilita en cualquier caso, sin bloquear el modal.
      setSubmitting(false);
    }
  };

  return (
    // Overlay: fondo oscuro (bg-black/40) que cubre la pantalla + caja
    // centrada con flexbox. `z-50` asegura que quede por encima de todo.
    <div className='fixed inset-0 z-50 flex items-center justify-center bg-black/40'>
      <div className='w-full max-w-md rounded-lg bg-white p-6 shadow-xl'>
        {/* Título dinámico según el modo (crear/editar) */}
        <h3 className='mb-4 text-lg font-semibold text-slate-900'>
          {isEditing ? 'Editar cliente' : 'Nuevo cliente'}
        </h3>

        <form onSubmit={handleSubmit} className='space-y-4'>
          <div>
            {/* El asterisco rojo marca campos obligatorios */}
            <label className='block text-sm font-medium text-slate-700'>
              Nombre <span className='text-red-500'>*</span>
            </label>
            <input
              type='text'
              value={nombre}
              onChange={(e) => setNombre(e.target.value)}
              className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
            />
          </div>

          <div>
            <label className='block text-sm font-medium text-slate-700'>
              Correo electrónico <span className='text-red-500'>*</span>
            </label>
            {/* El navegador valida `type='email'` y muestra el teclado
                apropiado en mobile. La validación REAL igual corre acá. */}
            <input
              type='email'
              value={correoElectronico}
              onChange={(e) => setCorreoElectronico(e.target.value)}
              className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
            />
          </div>

          <div>
            <label className='block text-sm font-medium text-slate-700'>
              CUIT
            </label>
            <input
              type='text'
              value={cuit}
              onChange={(e) => setCuit(e.target.value)}
              className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
            />
          </div>

          <div>
            <label className='block text-sm font-medium text-slate-700'>
              Descubierto máximo
            </label>
            <input
              type='number'
              value={maximoDescubierto}
              onChange={(e) => setMaximoDescubierto(e.target.value)}
              // El atributo `min` es validación nativa del navegador, pero
              // solo bloquea el submit si el form es nativo (acá la
              // validación real corre en handleSubmit).
              min={10000}
              className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
            />
          </div>

          <div>
            <label className='block text-sm font-medium text-slate-700'>
              Obras activas máximas <span className='text-red-500'>*</span>
            </label>
            <input
              type='number'
              value={maxObrasActivas}
              onChange={(e) => setMaxObrasActivas(e.target.value)}
              min={1}
              className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
            />
          </div>

          {/* Render condicional del error: si `error` es '' (falsy) no se
              pinta nada */}
          {error && (
            <p className='text-sm text-red-600'>{error}</p>
          )}

          <div className='flex justify-end gap-2 pt-2'>
            <Button
              variant='secondary'
              onClick={onCancel}
              disabled={submitting}
            >
              Cancelar
            </Button>
            {/* type="submit" dispara handleSubmit (onSubmit del <form>).
                `disabled={submitting}` evita el doble envío. */}
            <Button type='submit' disabled={submitting}>
              {submitting ? 'Guardando…' : 'Guardar'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
