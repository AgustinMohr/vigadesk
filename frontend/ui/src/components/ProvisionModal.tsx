// ==============================================================
// MODAL DE REPOSICIÓN DE STOCK (PROVISIÓN)
// --------------------------------------------------------------
// Ventana con un solo input numérico para agregar unidades al stock
// de un producto existente. Valida que la cantidad sea mayor a cero
// y llama a onSubmit(producto.id, cantidad) para persistir.
//
// El PRECIO NO se toca acá: si hay que cambiarlo se usa el botón
// "Editar" del listado (el backend lo deja intacto si no se manda).
//
// La cantidad admite decimales para materiales fraccionados (m³, kg).
// ==============================================================

'use client';

import { useState } from 'react';
import type { Producto } from '@/services/productoService';
import Button from './Button';
import { getErrorMessage } from '@/lib/http/api-error';

/**
 * PROPS del modal de reposición de stock.
 *
 * @typedef {Object} ProvisionModalProps
 * @property {Producto} producto - El producto a reponer.
 * @property {(id: number, cantidad: number) => Promise<void>} onSubmit -
 *   Callback con el id del producto y la cantidad a sumar al stock.
 * @property {() => void} onCancel - Cierra el modal sin reponer.
 */
type ProvisionModalProps = {
  producto: Producto;
  onSubmit: (id: number, cantidad: number) => Promise<void>;
  onCancel: () => void;
};

/**
 * Modal de un campo para reponer stock de un producto.
 *
 * El backend (PUT /api/productos/provision) SUMA la cantidad al stock
 * actual. El input parte vacío y el preview muestra cuántas unidades
 * quedarían tras la reposición.
 *
 * @param {ProvisionModalProps} props - Ver ProvisionModalProps.
 * @returns {JSX.Element} El modal con el input de cantidad.
 */
export default function ProvisionModal ({
  producto,
  onSubmit,
  onCancel,
}: ProvisionModalProps) {
  // El campo se maneja como string (los inputs type=number dan texto).
  const [valor, setValor] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  // Stock actual del producto (si viniera undefined, se asume 0).
  const stockActual = producto.stockActual ?? 0;
  const cantidad = Number(valor);
  // Preview: unidades que quedarían si se confirma la reposición.
  const stockFinal = Number.isNaN(cantidad) || cantidad < 0
    ? stockActual
    : stockActual + cantidad;

  /**
   * Valida la cantidad y delega la persistencia al padre.
   */
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!Number.isFinite(cantidad) || cantidad <= 0) {
      setError('Ingrese una cantidad mayor a cero.');
      return;
    }

    setSubmitting(true);
    try {
      // `producto.id!` — non-null assertion: los productos listados siempre
      // traen id (si no, el row no existiría en la tabla).
      await onSubmit(producto.id!, cantidad);
    } catch (err) {
      setError(getErrorMessage(err, 'Ocurrió un error al reponer el stock.'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    // Overlay de modal: fondo oscuro + caja blanca centrada (max-w-sm =
    // más angosto que los forms, porque solo trae un input).
    <div className='fixed inset-0 z-50 flex items-center justify-center bg-black/40'>
      <div className='w-full max-w-sm rounded-lg bg-white p-6 shadow-xl'>
        <h3 className='mb-4 text-lg font-semibold text-slate-900'>
          Reponer stock
        </h3>

        {/* Contexto: recuerda al admin SOBRE qué producto está trabajando */}
        <p className='mb-4 text-sm text-slate-600'>
          Producto: <span className='font-medium text-slate-900'>{producto.nombre}</span>
        </p>

        <form onSubmit={handleSubmit} className='space-y-4'>
          <div>
            <label className='block text-sm font-medium text-slate-700'>
              Cantidad a agregar <span className='text-red-500'>*</span>
            </label>
            {/* min='1' → el navegador ayuda a no escribir 0 o negativos
                (validación extra a la JS). */}
            <input
              type='number'
              step='0.001'
              min='0.001'
              value={valor}
              onChange={(e) => setValor(e.target.value)}
              autoFocus
              className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
            />
          </div>

          {/* Preview del resultado: stock actual → stock final. */}
          <p className='rounded-md bg-slate-50 px-3 py-2 text-xs text-slate-600'>
            Stock actual: <span className='font-medium text-slate-900'>{stockActual}</span>
            {' → '}
            Stock final: <span className='font-medium text-slate-900'>{stockFinal}</span>
          </p>

          {error && (
            <p className='text-sm text-red-600'>{error}</p>
          )}

          <div className='flex justify-end gap-2 pt-2'>
            <Button variant='secondary' onClick={onCancel} disabled={submitting}>
              Cancelar
            </Button>
            <Button type='submit' disabled={submitting}>
              {submitting ? 'Reponiendo…' : 'Reponer'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
