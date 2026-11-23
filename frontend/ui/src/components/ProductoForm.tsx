// ==============================================================
// FORMULARIO DE PRODUCTO (modal crear/editar)
// --------------------------------------------------------------
// Mismo patrón que ObraForm.tsx (ver ahí la teoría de INPUTS CONTROLADOS
// y del doble modo crear/editar). Particularidad: al montarse, hace un
// fetch a la API para cargar las CATEGORÍAS en el <select> (con
// useEffect + productoService.getCategorias).
//
// PATRÓN useEffect DE MOUNTAJE: el array de dependencias `[]` hace que el
// efecto corra UNA SOLA VEZ (tras el primer render). Acá se usa para
// poblar datos estáticos (categorías) que no dependen de props/estado.
//
// NOTA ASYNC: se usa `.then(setCategorias)` en vez de `async/await` para
// que la promesa quede manejada; el `.catch(() => {})` silencia el error
// (si falla el fetch, quedaría un select vacío, que no rompe el form).
// ==============================================================

'use client';

import { useEffect, useState } from 'react';
import type { Producto } from '@/services/productoService';
import { productoService } from '@/services/productoService';
import Button from './Button';
import Modal from './Modal';
import { getErrorMessage } from '@/lib/http/api-error';
import Field from './Field';

/**
 * PROPS del modal de producto.
 *
 * @typedef {Object} ProductoFormProps
 * @property {Producto} [producto] - Si existe → edición (campos precargados);
 *   si no → creación (campos vacíos).
 * @property {(data: Partial<Producto>) => Promise<void>} onSubmit - Callback
 *   con los datos del form; el padre persiste y cierra el modal.
 * @property {() => void} onCancel - Cierra el modal sin guardar.
 */
type ProductoFormProps = {
  producto?: Producto;
  onSubmit: (data: Partial<Producto>) => Promise<void>;
  onCancel: () => void;
};

/**
 * Modal de formulario para crear o editar un producto.
 *
 * Además del patrón estándar de inputs controlados, carga las categorías
 * disponibles desde el backend al montarse (efecto único) para poblar
 * el <select> de categoría.
 *
 * @param {ProductoFormProps} props - Ver ProductoFormProps.
 * @returns {JSX.Element} El modal con el formulario de producto.
 */
export default function ProductoForm ({
  producto,
  onSubmit,
  onCancel,
}: ProductoFormProps) {
  // Estado de cada campo (strings, incluso los números, hasta el submit).
  const [codigo, setCodigo] = useState(producto?.codigo ?? '');
  const [nombre, setNombre] = useState(producto?.nombre ?? '');
  const [descripcion, setDescripcion] = useState(producto?.descripcion ?? '');
  const [precio, setPrecio] = useState(producto?.precio?.toString() ?? '');
  const [costo, setCosto] = useState(producto?.costo?.toString() ?? '');
  const [stockMinimo, setStockMinimo] = useState(producto?.stockMinimo?.toString() ?? '');
  const [unidadMedida, setUnidadMedida] = useState(producto?.unidadMedida ?? 'UNIDAD');
  const [categoria, setCategoria] = useState(producto?.categoria ?? '');
  // Lista de categorías que llega de la API (se puebla en useEffect).
  const [categorias, setCategorias] = useState<string[]>([]);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // `!!producto` — doble negación → booleano.
  const isEditing = !!producto;

  // Efecto de montaje: carga las categorías UNA vez.
  useEffect(() => {
    // `.then(setCategorias)`: encadena la promesa; cuando resuelve, setea.
    // `.catch(() => {})`: si la API falla, silenciamos (el select queda
    // deshabilitado de facto al no tener opciones, sin crash de la app).
    productoService.getCategorias()
      .then(setCategorias)
      .catch(() => {});
  }, []);

  /**
   * Valida y delega el guardado al padre. `e.preventDefault()` evita la
   * recarga clásica del form nativo.
   */
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    // Validaciones client-side: feedback rápido SIN viajar a la red.
    if (!nombre.trim()) {
      setError('El nombre es obligatorio.');
      return;
    }
    if (!precio.trim() || Number(precio) <= 0) {
      setError('El precio debe ser un valor positivo.');
      return;
    }
    if (!stockMinimo.trim() || Number(stockMinimo) < 0) {
      setError('El stock mínimo debe ser un valor válido.');
      return;
    }
    if (costo.trim() && Number(costo) < 0) {
      setError('El costo no puede ser negativo.');
      return;
    }
    if (!categoria) {
      setError('La categoría es obligatoria.');
      return;
    }

    setSubmitting(true);
    try {
      await onSubmit({
        // `codigo.trim() || undefined`: si quedó vacío manda undefined
        // (el backend no pisa el valor existente en edición).
        codigo: codigo.trim() || undefined,
        nombre: nombre.trim(),
        descripcion: descripcion.trim() || undefined,
        precio: Number(precio), // string → número recién acá
        costo: costo.trim() ? Number(costo) : undefined,
        stockMinimo: Number(stockMinimo),
        unidadMedida,
        categoria,
      });
    } catch (err) {
      setError(getErrorMessage(err, 'Ocurrió un error al guardar el producto.'));
    } finally {
      setSubmitting(false); // siempre rehabilita el botón
    }
  };

  return (
    // Overlay de modal: fondo oscuro + caja blanca centrada.
    <Modal titulo={<>{isEditing ? 'Editar producto' : 'Nuevo producto'}</>} onClose={onCancel}>

      <form onSubmit={handleSubmit} className='space-y-4'>
        <Field label='Código'>
          <input
            type='text'
            value={codigo}
            onChange={(e) => setCodigo(e.target.value)}
            className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
          />
        </Field>

        <Field label='Nombre' required>
          <input
            type='text'
            value={nombre}
            onChange={(e) => setNombre(e.target.value)}
            className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
          />
        </Field>

        <Field label='Descripción'>
          <textarea
            value={descripcion}
            onChange={(e) => setDescripcion(e.target.value)}
            rows={2}
            className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
          />
        </Field>

        <div className='grid grid-cols-2 gap-4'>
          {/* step='0.01' permite decimales (moneda) */}
          <Field label='Precio' required>
            <input
              type='number'
              step='0.01'
              value={precio}
              onChange={(e) => setPrecio(e.target.value)}
              className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
            />
          </Field>
          <Field label='Stock mínimo' required>
            <input
              type='number'
              step='0.001'
              min='0'
              value={stockMinimo}
              onChange={(e) => setStockMinimo(e.target.value)}
              className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
            />
          </Field>
        </div>

        <div className='grid grid-cols-2 gap-4'>
          <Field label='Unidad de medida'>
            <select
              value={unidadMedida}
              onChange={(e) => setUnidadMedida(e.target.value)}
              className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
            >
              {['UNIDAD', 'BOLSA', 'M3', 'KG', 'METRO', 'LITRO', 'TONELADA'].map((unidad) => (
                <option key={unidad} value={unidad}>{unidad}</option>
              ))}
            </select>
          </Field>
          <Field label='Costo unitario'>
            <input
              type='number'
              step='0.01'
              min='0'
              value={costo}
              onChange={(e) => setCosto(e.target.value)}
              className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
            />
          </Field>
        </div>

        {/* Select de categorías cargado desde la API. La primera opción
                (value='') actúa como placeholder ("Seleccionar categoría"). */}
        <Field label='Categoría' required>
          <select
            value={categoria}
            onChange={(e) => setCategoria(e.target.value)}
            className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
          >
            <option value=''>Seleccionar categoría</option>
            {categorias.map((cat) => (
              <option key={cat} value={cat}>
                {cat}
              </option>
            ))}
          </select>
        </Field>

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
          <Button type='submit' disabled={submitting}>
            {submitting ? 'Guardando…' : 'Guardar'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
