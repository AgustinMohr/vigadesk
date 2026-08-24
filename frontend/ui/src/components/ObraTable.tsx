// ==============================================================
// TABLA DE OBRAS (componente "tonto" / de presentación)
// --------------------------------------------------------------
// Este tipo de componente NO trae datos por sí mismo: recibe todo por
// PROPS desde el padre (ObrasPageClient). Los props son como "argumentos"
// que el padre pasa: acá recibe las obras y 4 callbacks (funciones) para
// avisarle al padre qué acción quiere el usuario.
//
// PATRÓN: separar presentación (tabla) de lógica (página). La tabla solo
// pinta filas y avisa "el usuario hizo click en Editar de esta obra";
// el padre decide qué hacer con eso. Es el patrón Container/Presentational.
//
// CONCEPTOS CLAVE: useSort con `getValue` (para ordenar por campos
// ANIDADOS como cliente.nombre), registries de clases (estadoBadge),
// `key as keyof Obra` (casting de acceso dinámico), render condicional.
// ==============================================================

'use client';

import type { Obra } from '@/services/obraService';
import Button from './Button';
import { useSort } from '@/hooks/useSort';
import { useAuth } from '@/lib/auth/AuthContext';

// Clases CSS de Tailwind para las celdas de encabezado
const thClass =
  'px-4 py-3 text-left font-medium text-slate-600 cursor-pointer select-none hover:text-slate-900';

// Colores de la "pill" (etiqueta) según el estado de la obra. Es un Record
// clave→string: la clave es el estado y el string las clases Tailwind.
const estadoBadge: Record<string, string> = {
  HABILITADA: 'bg-green-100 text-green-700',
  PENDIENTE: 'bg-amber-100 text-amber-700',
  FINALIZADA: 'bg-slate-200 text-slate-600',
};

/**
 * Extrae el valor a comparar para ordenar una obra según la columna.
 * Se define a nivel de módulo (no dentro del componente) para que su
 * referencia sea estable y el useMemo de useSort no se invalide en cada
 * render.
 */
function obraSortValue (obra: Obra, key: string): string | number {
  // Casos especiales (campos que no están en la raíz del objeto):
  if (key === 'cliente') return obra.cliente?.nombre ?? '';
  if (key === 'presupuesto') return obra.presupuesto ?? 0;

  // Caso genérico: `obra[key as keyof Obra]` es acceso dinámico por
  // variable. El casting `as keyof Obra` le dice a TS "esta key es
  // una de las propiedades reales de Obra"; `as string | number`
  // unifica el tipo porque las props de Obra pueden ser de ambos.
  return obra[key as keyof Obra] as string | number;
}

/**
 * PROPS del componente: qué recibe de su padre.
 *
 * @typedef {Object} ObraTableProps
 * @property {Obra[]} obras - Los datos a mostrar.
 * @property {(obra: Obra) => void} onEdit - Abre el form en modo edición.
 * @property {(obra: Obra) => void} onDelete - Confirma y elimina la obra.
 * @property {(obra: Obra) => void} onEstado - Abre el modal de cambio de estado.
 * @property {(obra: Obra) => void} onAsignarCliente - Abre el modal para asignar cliente.
 */
type ObraTableProps = {
  obras: Obra[];
  onEdit: (obra: Obra) => void;
  onDelete: (obra: Obra) => void;
  onEstado: (obra: Obra) => void;
  onAsignarCliente: (obra: Obra) => void;
};

/**
 * Tabla de obras con ordenamiento por columna.
 *
 * @param {ObraTableProps} props - Ver definición del contrato.
 * @returns {JSX.Element} El <table> con las obras ordenadas.
 */
export default function ObraTable ({
  obras,
  onEdit,
  onDelete,
  onEstado,
  onAsignarCliente,
}: ObraTableProps) {
  const { isAdmin } = useAuth();

  const { sortedData, toggleSort, indicator } = useSort(obras, 'direccion', obraSortValue);

  return (
    <div className='overflow-x-auto rounded-lg border border-slate-200'>
      <table className='min-w-full divide-y divide-slate-200 text-sm'>
        <thead className='bg-slate-50'>
          <tr>
            {/* Al hacer click en un encabezado se ordena por esa columna */}
            <th className={thClass} onClick={() => toggleSort('direccion')}>
              Dirección{indicator('direccion')}
            </th>
            <th className={thClass} onClick={() => toggleSort('cliente')}>
              Cliente{indicator('cliente')}
            </th>
            <th className={thClass} onClick={() => toggleSort('presupuesto')}>
              Presupuesto{indicator('presupuesto')}
            </th>
            <th className={thClass} onClick={() => toggleSort('estado')}>
              Estado{indicator('estado')}
            </th>
            <th className='px-4 py-3 text-right font-medium text-slate-600'>
              Acciones
            </th>
          </tr>
        </thead>
        <tbody className='divide-y divide-slate-200'>
          {/* Render de listas: `sortedData.map(...)` crea una fila <tr> por
              obra. La prop `key` (única por fila) ayuda a React a saber
              qué fila cambió al re-renderizar. */}
          {sortedData.length === 0
            ? (
              // Fila de "sin datos": colSpan=5 para cubrir todas las columnas.
              <tr>
                <td colSpan={5} className='px-4 py-8 text-center text-slate-400'>
                  No hay obras registradas.
                </td>
              </tr>
              )
            : (
                sortedData.map((obra) => (
                  <tr key={obra.id} className='hover:bg-slate-50'>
                    <td className='px-4 py-3 font-medium text-slate-900'>
                      {obra.direccion}
                    </td>
                    {/* `obra.cliente?.nombre ?? "Sin asignar"`: el optional
                        chaining (?.) evita el error si cliente es null, y
                        el nullish (??) cae en el texto "Sin asignar". */}
                    <td className='px-4 py-3 text-slate-600'>
                      {obra.cliente?.nombre ?? (
                        <span className='text-slate-400'>Sin asignar</span>
                      )}
                    </td>
                    {/* toLocaleString formatea el número como precio argentino
                        (puntos de miles): 1234567 → $1.234.567 */}
                    <td className='px-4 py-3 text-slate-600'>
                      ${obra.presupuesto.toLocaleString('es-AR')}
                    </td>
                    <td className='px-4 py-3'>
                      {/* Etiqueta de estado con color según estadoBadge.
                          El `?? 'bg-slate-100...'` es un fallback: si el estado
                          no está en el Record (ej: un estado nuevo del backend),
                          usa colores neutros en vez de romper el layout. */}
                      {obra.estado
                        ? (
                          <span
                            className={`rounded-full px-2.5 py-1 text-xs font-medium ${estadoBadge[obra.estado] ?? 'bg-slate-100 text-slate-600'}`}
                          >
                            {obra.estado}
                          </span>
                          )
                        : (
                          <span className='text-slate-400'>—</span>
                          )}
                    </td>
                    <td className='px-4 py-3 text-right'>
                      {/* Los botones solo se muestran si el usuario es admin.
                          Cada onClick pasa la obra actual al callback del padre.
                          `isAdmin && (...)`: si es false, React renderiza false
                          = no dibuja nada (render condicional idiomático). */}
                      <div className='flex justify-end gap-2'>
                        {isAdmin && (
                          <Button
                            variant='secondary'
                            onClick={() => onAsignarCliente(obra)}
                          >
                            Cliente
                          </Button>
                        )}
                        {isAdmin && (
                          <Button variant='secondary' onClick={() => onEstado(obra)}>
                            Estado
                          </Button>
                        )}
                        {isAdmin && (
                          <Button variant='secondary' onClick={() => onEdit(obra)}>
                            Editar
                          </Button>
                        )}
                        {isAdmin && (
                          <Button variant='danger' onClick={() => onDelete(obra)}>
                            Eliminar
                          </Button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))
              )}
        </tbody>
      </table>
    </div>
  );
}
