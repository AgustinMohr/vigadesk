'use client';

import type { ReactNode } from 'react';
import type { SortDir } from '@/hooks/useSort';

type SortableThProps = {
  /** Clave de la columna (la misma que recibe useSort). */
  clave: string;
  /** Columna y sentido de orden actuales (de useSort). */
  sortKey: string | undefined;
  sortDir: SortDir;
  onSort: (clave: string) => void;
  /** Clases del <th> (alineación, padding y color). */
  className?: string;
  children: ReactNode;
};

/**
 * Encabezado de tabla ordenable y operable con teclado: el <th> declara `aria-sort` y dentro lleva un botón
 * (foco con Tab, Enter o Espacio para ordenar). La flecha es decorativa: el estado lo anuncia `aria-sort`.
 */
export default function SortableTh ({ clave, sortKey, sortDir, onSort, className = '', children }: SortableThProps) {
  const activa = clave === sortKey;
  const ariaSort = activa ? (sortDir === 'asc' ? 'ascending' : 'descending') : 'none';
  return (
    <th scope='col' aria-sort={ariaSort} className={className}>
      <button
        type='button'
        onClick={() => onSort(clave)}
        className='inline-flex items-center gap-1 font-medium hover:text-slate-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-slate-500'
      >
        {children}
        <span aria-hidden='true'>{activa ? (sortDir === 'asc' ? '▲' : '▼') : ''}</span>
      </button>
    </th>
  );
}
