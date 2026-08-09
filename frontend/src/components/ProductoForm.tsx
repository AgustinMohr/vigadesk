// ==============================================================
// FORMULARIO DE PRODUCTO (modal crear/editar)
// Mismo patrón que ObraForm.tsx. Particularidad: al montarse, hace un
// fetch a la API para cargar las CATEGORÍAS en el <select> (con
// useEffect + productoService.getCategorias).
// ==============================================================

'use client';

import { useEffect, useState } from 'react';
import type { Producto } from '@/services/productoService';
import { productoService } from '@/services/productoService';
import Button from './Button';

// Modal formulario para crear o editar un producto
// Si `${producto}` existe → edición; si no → creación
// Carga la lista de categorías desde la API al montarse
type ProductoFormProps = {
  producto?: Producto;
  onSubmit: (data: Partial<Producto>) => Promise<void>;
  onCancel: () => void;
};

export default function ProductoForm ({
  producto,
  onSubmit,
  onCancel,
}: ProductoFormProps) {
  const [codigo, setCodigo] = useState(producto?.codigo ?? '');
  const [nombre, setNombre] = useState(producto?.nombre ?? '');
  const [descripcion, setDescripcion] = useState(producto?.descripcion ?? '');
  const [precio, setPrecio] = useState(producto?.precio?.toString() ?? '');
  const [stockMinimo, setStockMinimo] = useState(producto?.stockMinimo?.toString() ?? '');
  const [categoria, setCategoria] = useState(producto?.categoria ?? '');
  const [categorias, setCategorias] = useState<string[]>([]);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const isEditing = !!producto;

  useEffect(() => {
    productoService.getCategorias()
      .then(setCategorias)
      .catch(() => {});
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

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
    if (!categoria) {
      setError('La categoría es obligatoria.');
      return;
    }

    setSubmitting(true);
    try {
      await onSubmit({
        codigo: codigo.trim() || undefined,
        nombre: nombre.trim(),
        descripcion: descripcion.trim() || undefined,
        precio: Number(precio),
        stockMinimo: Number(stockMinimo),
        categoria,
      });
    } catch {
      setError('Ocurrió un error al guardar el producto.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className='fixed inset-0 z-50 flex items-center justify-center bg-black/40'>
      <div className='w-full max-w-md rounded-lg bg-white p-6 shadow-xl'>
        <h3 className='mb-4 text-lg font-semibold text-slate-900'>
          {isEditing ? 'Editar producto' : 'Nuevo producto'}
        </h3>

        <form onSubmit={handleSubmit} className='space-y-4'>
          <div>
            <label className='block text-sm font-medium text-slate-700'>
              Código
            </label>
            <input
              type='text'
              value={codigo}
              onChange={(e) => setCodigo(e.target.value)}
              className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
            />
          </div>

          <div>
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
              Descripción
            </label>
            <textarea
              value={descripcion}
              onChange={(e) => setDescripcion(e.target.value)}
              rows={2}
              className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
            />
          </div>

          <div className='grid grid-cols-2 gap-4'>
            <div>
              <label className='block text-sm font-medium text-slate-700'>
                Precio <span className='text-red-500'>*</span>
              </label>
              <input
                type='number'
                step='0.01'
                value={precio}
                onChange={(e) => setPrecio(e.target.value)}
                className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
              />
            </div>
            <div>
              <label className='block text-sm font-medium text-slate-700'>
                Stock mínimo <span className='text-red-500'>*</span>
              </label>
              <input
                type='number'
                value={stockMinimo}
                onChange={(e) => setStockMinimo(e.target.value)}
                className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
              />
            </div>
          </div>

          <div>
            <label className='block text-sm font-medium text-slate-700'>
              Categoría <span className='text-red-500'>*</span>
            </label>
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
          </div>

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
      </div>
    </div>
  );
}
