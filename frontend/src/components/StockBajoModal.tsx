// ==============================================================
// MODAL "PRODUCTOS CON STOCK BAJO"
// Combina tabla (con useSort) + detalle en una sola ventana: hace
// render condicional — si hay un producto seleccionado (`selected`)
// muestra su detalle, si no muestra la tabla completa.
// Particularidad: el modal "recibe" los productos ya filtrados por el
// padre (el fetch lo hace ProductosPageClient).
// ==============================================================

'use client';

import { useState } from 'react';
import type { Producto } from '@/services/productoService';
import Button from './Button';
import { useSort } from '@/hooks/useSort';

const thClass =
  'px-4 py-3 text-left font-medium text-slate-600 cursor-pointer select-none hover:text-slate-900';
const thClassRight =
  'px-4 py-3 text-right font-medium text-slate-600 cursor-pointer select-none hover:text-slate-900';

function formatCurrency (n: number) {
  return `$${n.toLocaleString('es-AR', { minimumFractionDigits: 2 })}`;
}

type StockBajoModalProps = {
  productos: Producto[];
  onClose: () => void;
};

export default function StockBajoModal ({
  productos,
  onClose,
}: StockBajoModalProps) {
  const { sortedData, toggleSort, indicator } = useSort(productos, 'nombre');
  const [selected, setSelected] = useState<Producto | null>(null);

  if (selected) {
    return (
      <div className='fixed inset-0 z-50 flex items-center justify-center bg-black/40'>
        <div className='w-full max-w-lg rounded-lg bg-white p-6 shadow-xl'>
          <div className='mb-4 flex items-center justify-between'>
            <h3 className='text-lg font-semibold text-slate-900'>
              Detalle del producto
            </h3>
            <button
              onClick={() => setSelected(null)}
              className='text-xl leading-none text-slate-400 hover:text-slate-600'
            >
              &times;
            </button>
          </div>

          <div className='space-y-3 text-sm'>
            <div>
              <span className='font-medium text-slate-500'>Código:</span>
              <p className='text-slate-900'>{selected.codigo ?? '—'}</p>
            </div>
            <div>
              <span className='font-medium text-slate-500'>Nombre:</span>
              <p className='text-slate-900'>{selected.nombre}</p>
            </div>
            <div>
              <span className='font-medium text-slate-500'>Descripción:</span>
              <p className='text-slate-900'>{selected.descripcion ?? '—'}</p>
            </div>
            <div>
              <span className='font-medium text-slate-500'>Precio:</span>
              <p className='text-slate-900'>
                {formatCurrency(Number(selected.precio))}
              </p>
            </div>
            <div>
              <span className='font-medium text-slate-500'>Stock Actual:</span>
              <p className='font-medium text-red-600'>
                {selected.stockActual}
              </p>
            </div>
            <div>
              <span className='font-medium text-slate-500'>Stock Mínimo:</span>
              <p className='text-slate-900'>{selected.stockMinimo}</p>
            </div>
            <div>
              <span className='font-medium text-slate-500'>Categoría:</span>
              <p className='text-slate-900'>{selected.categoria ?? '—'}</p>
            </div>
            <div>
              <span className='font-medium text-slate-500'>
                Descuento Promocional:
              </span>
              <p className='text-slate-900'>
                {selected.descuentoPromocional != null &&
                selected.descuentoPromocional > 0
                  ? formatCurrency(Number(selected.descuentoPromocional))
                  : '—'}
              </p>
            </div>
          </div>

          <div className='mt-6 flex justify-end gap-2'>
            <Button variant='secondary' onClick={() => setSelected(null)}>
              Volver
            </Button>
            <Button variant='secondary' onClick={onClose}>
              Cerrar
            </Button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className='fixed inset-0 z-50 flex items-center justify-center bg-black/40'>
      <div className='flex max-h-[80vh] w-full max-w-4xl flex-col rounded-lg bg-white p-6 shadow-xl'>
        <div className='mb-4 flex items-center justify-between'>
          <h3 className='text-lg font-semibold text-slate-900'>
            Productos con stock bajo ({productos.length})
          </h3>
          <button
            onClick={onClose}
            className='text-xl leading-none text-slate-400 hover:text-slate-600'
          >
            &times;
          </button>
        </div>

        <div className='min-h-0 flex-1 overflow-y-auto'>
          <div className='overflow-x-auto rounded-lg border border-slate-200'>
            <table className='min-w-full divide-y divide-slate-200 text-sm'>
              <thead className='bg-slate-50'>
                <tr>
                  <th
                    className={thClass}
                    onClick={() => toggleSort('codigo')}
                  >
                    Código{indicator('codigo')}
                  </th>
                  <th
                    className={thClass}
                    onClick={() => toggleSort('nombre')}
                  >
                    Nombre{indicator('nombre')}
                  </th>
                  <th
                    className={thClassRight}
                    onClick={() => toggleSort('precio')}
                  >
                    Precio{indicator('precio')}
                  </th>
                  <th
                    className={thClassRight}
                    onClick={() => toggleSort('stockActual')}
                  >
                    Stock Actual{indicator('stockActual')}
                  </th>
                  <th
                    className={thClassRight}
                    onClick={() => toggleSort('stockMinimo')}
                  >
                    Stock Mínimo{indicator('stockMinimo')}
                  </th>
                  <th
                    className={thClass}
                    onClick={() => toggleSort('categoria')}
                  >
                    Categoría{indicator('categoria')}
                  </th>
                  <th className='px-4 py-3 text-right font-medium text-slate-600'>
                    Acciones
                  </th>
                </tr>
              </thead>
              <tbody className='divide-y divide-slate-200'>
                {sortedData.length === 0
                  ? (
                    <tr>
                      <td
                        colSpan={7}
                        className='px-4 py-8 text-center text-slate-400'
                      >
                        No hay productos con stock bajo.
                      </td>
                    </tr>
                    )
                  : (
                      sortedData.map((p) => (
                        <tr key={p.id} className='hover:bg-slate-50'>
                          <td className='px-4 py-3 font-medium text-slate-900'>
                            {p.codigo ?? '—'}
                          </td>
                          <td className='px-4 py-3 text-slate-600'>{p.nombre}</td>
                          <td className='px-4 py-3 text-right text-slate-600'>
                            {formatCurrency(Number(p.precio))}
                          </td>
                          <td className='px-4 py-3 text-right font-medium text-red-600'>
                            {p.stockActual}
                          </td>
                          <td className='px-4 py-3 text-right text-slate-600'>
                            {p.stockMinimo}
                          </td>
                          <td className='px-4 py-3 text-slate-600'>
                            {p.categoria ?? '—'}
                          </td>
                          <td className='px-4 py-3 text-right'>
                            <Button
                              variant='secondary'
                              onClick={() => setSelected(p)}
                            >
                              Ver detalle
                            </Button>
                          </td>
                        </tr>
                      ))
                    )}
              </tbody>
            </table>
          </div>
        </div>

        <div className='mt-4 flex justify-end'>
          <Button variant='secondary' onClick={onClose}>
            Cerrar
          </Button>
        </div>
      </div>
    </div>
  );
}
