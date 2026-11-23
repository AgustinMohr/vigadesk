// ==============================================================
// MODAL DE DESCUENTO PROMOCIONAL
// --------------------------------------------------------------
// Ventana pequeña con un solo input numérico. Valida que el valor sea
// un PORCENTAJE entre 0 y 100 y llama a onSubmit(producto.id, descuento)
// para persistir.
//
// CONCEPTO CLAVE — Number.isNaN vs isNaN: `Number.isNaN(x)` devuelve
// true SOLO si x es literalmente NaN (el resultado de Number("abc")),
// sin coerción rara. Es la forma correcta de validar una conversión
// numérica hecha por Number(). (El `isNaN` global tiene comportamiento
// confuso con strings.)
//
// SEMÁNTICA DEL CAMPO: descuentoPromocional es un PORCENTAJE (0-100).
// El backend (ms-pedidos) lo aplica como precio × (1 − descuento/100);
// si entrara un valor > 100, el factor se vuelve negativo y el total del
// pedido da absurdo. Por eso acá se valida 0 ≤ valor ≤ 100.
//
// NOTA DE UX/SEGURIDAD: al precargar el descuento actual del producto en
// el input, el admin SIEMPRE ve cuánto descuento tiene hoy antes de
// modificarlo. Un 0 = quitar la promoción.
// ==============================================================

'use client';

import { useState } from 'react';
import type { Producto } from '@/services/productoService';
import Button from './Button';
import Modal from './Modal';
import { getErrorMessage } from '@/lib/http/api-error';
import Field from './Field';

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
 * El descuento se guarda como PORCENTAJE entre 0 y 100 (ej: 10 = 10% de
 * descuento sobre el precio del producto). El input parte del valor
 * actual del producto (o "0") para que el admin vea y ajuste el estado
 * vigente.
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
    // `descuento < 0 || descuento > 100`: el descuento es un porcentaje
    // válido entre 0 y 100; fuera de ese rango el cálculo del pedido se
    // rompe (valores > 100 dan totales negativos en el backend).
    if (Number.isNaN(descuento) || descuento < 0 || descuento > 100) {
      setError('Ingrese un porcentaje válido entre 0 y 100.');
      return;
    }

    setSubmitting(true);
    try {
      // `producto.id!` — non-null assertion: los productos listados siempre
      // traen id (si no, el row no existiría en la tabla).
      await onSubmit(producto.id!, descuento);
    } catch (err) {
      setError(getErrorMessage(err, 'Ocurrió un error al aplicar el descuento.'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    // Overlay de modal: fondo oscuro + caja blanca centrada (max-w-sm =
    // más angosto que los forms, porque solo trae un input).
    <Modal titulo='Aplicar descuento promocional' onClose={onCancel} ancho='max-w-sm'>

      {/* Contexto: recuerda al admin SOBRE qué producto está trabajando */}
      <p className='mb-4 text-sm text-slate-600'>
        Producto: <span className='font-medium text-slate-900'>{producto.nombre}</span>
      </p>

      <form onSubmit={handleSubmit} className='space-y-4'>
        {/* step='0.01' → permite porcentajes con decimales; min/max='0/100'
                → el navegador ayuda a no escribir valores fuera de rango
                (validación extra a la JS). */}
        <Field label='Descuento promocional (%)'>
          <input
            type='number'
            step='0.01'
            min='0'
            max='100'
            value={valor}
            onChange={(e) => setValor(e.target.value)}
            className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
          />
        </Field>

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
    </Modal>
  );
}
