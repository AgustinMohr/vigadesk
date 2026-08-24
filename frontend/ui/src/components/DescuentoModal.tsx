// ==============================================================
// MODAL DE DESCUENTO PROMOCIONAL
// --------------------------------------------------------------
// Ventana pequeña con un solo input numérico. Valida que el valor sea
// >= 0 y llama a onSubmit(producto.id, descuento) para persistir.
//
// CONCEPTO CLAVE — Number.isNaN vs isNaN: `Number.isNaN(x)` devuelve
// true SOLO si x es literalmente NaN (el resultado de Number("abc")),
// sin coerción rara. Es la forma correcta de validar una conversión
// numérica hecha por Number(). (El `isNaN` global tiene comportamiento
// confuso con strings.)
//
// NOTA DE UX/SEGURIDAD: al precargar el descuento actual del producto en
// el input, el admin SIEMPRE ve cuánto descuento tiene hoy antes de
// modificarlo. Un 0 = quitar la promoción.
// ==============================================================

'use client';

import { useState } from 'react';
import type { Producto } from '@/services/productoService';
import Button from './Button';

/**
 * PROPS del modal de descuento.
 *
 * @typedef {Object} DescuentoModalProps
 * @property {Producto} producto - El producto al que se le aplica el descuento.
 * @property {(id: number, descuentoPromocional: number) => Promise<void>} onSubmit -
 *   Callback con el id del producto y el monto de descuento a persistir.
 * @property {() => void} onCancel - Cierra el modal sin aplicar.
 */
type DescuentoModalProps = {
  producto: Producto;
  onSubmit: (id: number, descuentoPromocional: number) => Promise<void>;
  onCancel: () => void;
};

/**
 * Modal de un campo para fijar el descuento promocional de un producto.
 *
 * El descuento se guarda como MONTO en pesos (no porcentaje). El input
 * parte del valor actual del producto (o "0") para que el admin vea y
 * ajuste el estado vigente.
 *
 * @param {DescuentoModalProps} props - Ver DescuentoModalProps.
 * @returns {JSX.Element} El modal con el input de descuento.
 */
export default function DescuentoModal ({
  producto,
  onSubmit,
  onCancel,
}: DescuentoModalProps) {
  // El campo se maneja como string (los inputs type=number dan texto);
  // `?.toString()` convierte el valor previo, `?? '0'` protege undefined.
  const [valor, setValor] = useState(
    producto.descuentoPromocional?.toString() ?? '0'
  );
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  /**
   * Valida el monto y delega la persistencia al padre.
   */
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    // Number("") === 0 (string vacío se convierte a 0), ojo con eso: si el
    // usuario borra todo, el descuento quedaría en 0 (quitado), no hay NaN.
    const descuento = Number(valor);

    // `Number.isNaN` solo entra si escribieron algo no numérico (ej "abc").
    // `descuento < 0`: descuentos negativos no tienen sentido de negocio.
    if (Number.isNaN(descuento) || descuento < 0) {
      setError('Ingrese un valor numérico válido (mayor o igual a 0).');
      return;
    }

    setSubmitting(true);
    try {
      // `producto.id!` — non-null assertion: los productos listados siempre
      // traen id (si no, el row no existiría en la tabla).
      await onSubmit(producto.id!, descuento);
    } catch {
      setError('Ocurrió un error al aplicar el descuento.');
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
          Aplicar descuento promocional
        </h3>

        {/* Contexto: recuerda al admin SOBRE qué producto está trabajando */}
        <p className='mb-4 text-sm text-slate-600'>
          Producto: <span className='font-medium text-slate-900'>{producto.nombre}</span>
        </p>

        <form onSubmit={handleSubmit} className='space-y-4'>
          <div>
            <label className='block text-sm font-medium text-slate-700'>
              Descuento promocional ($)
            </label>
            {/* step='0.01' → moneda con decimales; min='0' → el navegador
                ayuda a no escribir negativos (validación extra a la JS). */}
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
