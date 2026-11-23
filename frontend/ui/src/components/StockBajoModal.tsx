// ==============================================================
// MODAL "PRODUCTOS CON STOCK BAJO"
// --------------------------------------------------------------
// Ventana que combina tabla (con useSort) + detalle en un solo lugar:
// hace render condicional — si hay un producto seleccionado (`selected`)
// muestra su DETALLE, si no muestra la TABLA completa de productos
// con stock bajo.
//
// PATRÓN DE "MODO" POR ESTADO (early return): en vez de una sola vista
// con ternarios gigantes, el componente hace `if (selected) return (...)`
// ANTES del render principal. Cada rama es un layout separado y legible.
// React optimiza: si el resultado del if cambia, re-renderiza la rama.
//
// ARQUITECTURA DE DATOS: el modal NO hace fetch — `productos` ya llega
// FILTRADO desde el padre (ProductosPageClient hizo productosService.getAll
// y filtró stockActual < stockMinimo en useMemo).
//
// UX DEL FLUJO: Tabla → "Ver detalle" de una fila → Vista detalle →
// "Volver" (regresa a la tabla) o "Cerrar" (cierra todo el modal).
// ==============================================================

'use client';

import { useState } from 'react';
import type { Producto } from '@/services/productoService';
import Button from './Button';
import Modal from './Modal';
import { useSort } from '@/hooks/useSort';
import SortableTh from './SortableTh';

// Constantes de clases Tailwind para encabezados (izquierda y derecha).
const thClass =
  'px-4 py-3 text-left font-medium text-slate-600';
const thClassRight =
  'px-4 py-3 text-right font-medium text-slate-600';

/**
 * Formatea un número a moneda argentina con 2 decimales.
 *
 * @param {number} n - El monto a formatear.
 * @returns {string} El monto formateado (ej: "$1.599,00").
 */
function formatCurrency (n: number) {
  return `$${n.toLocaleString('es-AR', { minimumFractionDigits: 2 })}`;
}

/**
 * PROPS del modal de stock bajo.
 *
 * @typedef {Object} StockBajoModalProps
 * @property {Producto[]} productos - Productos YA filtrados por el padre
 *   (solo los que están por debajo del stock mínimo).
 * @property {() => void} onClose - Cierra el modal.
 */
type StockBajoModalProps = {
  productos: Producto[];
  onClose: () => void;
};

/**
 * Modal de "productos con stock bajo": tabla ordenable + detalle.
 *
 * Estados: `sortedData` (productos ordenados por useSort) y `selected`
 * (el producto cuyo detalle se muestra, o null para ver la tabla).
 *
 * @param {StockBajoModalProps} props - Ver StockBajoModalProps.
 * @returns {JSX.Element} La tabla o el detalle del producto seleccionado.
 */
export default function StockBajoModal ({
  productos,
  onClose,
}: StockBajoModalProps) {
  // useSort con columna inicial "nombre" (acceso directo a propiedades).
  const { sortedData, sortKey, sortDir, toggleSort } = useSort(productos, 'nombre');

  // `selected`: null → mostramos la tabla; Producto → mostramos su detalle.
  const [selected, setSelected] = useState<Producto | null>(null);

  // EARLY RETURN: si hay un producto seleccionado, se renderiza SOLO la
  // vista de detalle (y se deja de ejecutar hacia abajo).
  if (selected) {
    return (
      <Modal titulo='Detalle del producto' onClose={() => setSelected(null)} ancho='max-w-lg' mostrarCerrar>

        {/* Todos los datos del producto, con `?? '—'` para opcionales */}
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
            {/* El stock actual va en ROJO: es el dato que generó la alerta */}
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
              {/* Solo muestra el porcentaje si existe y es > 0 */}
              {selected.descuentoPromocional != null &&
                selected.descuentoPromocional > 0
                ? `${Number(selected.descuentoPromocional).toLocaleString('es-AR')}%`
                : '—'}
            </p>
          </div>
        </div>

        <div className='mt-6 flex justify-end gap-2'>
          {/* "Volver" regresa a la lista completa del modal */}
          <Button variant='secondary' onClick={() => setSelected(null)}>
            Volver
          </Button>
          <Button variant='secondary' onClick={onClose}>
            Cerrar
          </Button>
        </div>
      </Modal>
    );
  }

  // VISTA PRINCIPAL: la tabla de productos con stock bajo, ordenable.
  return (
    <Modal titulo={<>Productos con stock bajo ({productos.length})</>} onClose={onClose} ancho='max-w-4xl' className='flex max-h-[80vh] flex-col' mostrarCerrar>

      {/* `min-h-0 flex-1 overflow-y-auto`: sin min-h-0, el flex child no
            puede encoger y el scroll interno no funcionaría (gotcha clásico
            de CSS flex). */}
      <div className='min-h-0 flex-1 overflow-y-auto'>
        <div className='overflow-x-auto rounded-lg border border-slate-200'>
          <table className='min-w-full divide-y divide-slate-200 text-sm'>
            <thead className='bg-slate-50'>
              <tr>
                <SortableTh clave='codigo' sortKey={sortKey} sortDir={sortDir} onSort={toggleSort} className={thClass}>Código</SortableTh>
                <SortableTh clave='nombre' sortKey={sortKey} sortDir={sortDir} onSort={toggleSort} className={thClass}>Nombre</SortableTh>
                <SortableTh clave='precio' sortKey={sortKey} sortDir={sortDir} onSort={toggleSort} className={thClassRight}>Precio</SortableTh>
                <SortableTh clave='stockActual' sortKey={sortKey} sortDir={sortDir} onSort={toggleSort} className={thClassRight}>Stock Actual</SortableTh>
                <SortableTh clave='stockMinimo' sortKey={sortKey} sortDir={sortDir} onSort={toggleSort} className={thClassRight}>Stock Mínimo</SortableTh>
                <SortableTh clave='categoria' sortKey={sortKey} sortDir={sortDir} onSort={toggleSort} className={thClass}>Categoría</SortableTh>
                <th className='px-4 py-3 text-right font-medium text-slate-600'>
                  Acciones
                </th>
              </tr>
            </thead>
            <tbody className='divide-y divide-slate-200'>
              {sortedData.length === 0
                ? (
              // Sin datos: celda centrada que abarca las 7 columnas.
                  <tr>
                    <td
                      colSpan={7}
                      className='px-4 py-8 text-center text-slate-500'
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
                        {/* `Number(...)` mantiene el tipo aunque el backend
                              mande precio como string */}
                        <td className='px-4 py-3 text-right text-slate-600'>
                          {formatCurrency(Number(p.precio))}
                        </td>
                        {/* Stock actual resaltado en rojo: es la alerta */}
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
                          {/* Cambia el modo interno del modal (early return) */}
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
    </Modal>
  );
}
