// ==============================================================
// TABLA DE CLIENTES (componente "tonto" / de presentación)
// --------------------------------------------------------------
// Mismo patrón que ObraTable.tsx: el componente NO trae datos por sí
// mismo — recibe `clientes` y callbacks por PROPS, ordena con useSort
// y avisa al padre con onClick. Particularidad: el botón "Ver" está
// disponible para todos los roles; los demás (asignar obra, editar,
// eliminar) solo aparecen si el usuario es admin (isAdmin).
//
// ARQUITECTURA (Container/Presentacional): la página (ClientesPageClient)
// maneja la lógica (fetch, modales) y la tabla SOLO presenta datos y
// delega decisiones: "el usuario hizo click en Editar de este cliente".
// El padre decide qué hacer. Acá no hay useEffect ni estados de datos.
//
// CONCEPTOS CLAVE: props, callbacks, render condicional con `&&`,
// operador nullish (??) para datos ausentes, y `toLocaleString` para
// darle formato de moneda según la región.
// ==============================================================

'use client';

import type { Cliente } from '@/services/clienteService';
import Button from './Button';
import { useSort } from '@/hooks/useSort';
import { useAuth } from '@/lib/auth/AuthContext';
import SortableTh from './SortableTh';

// Clases CSS de Tailwind para las celdas de encabezado. Se centralizan
// en una constante para no repetir el string en cada <th> (DRY) y que
// cambiar el estilo del encabezado sea tocar un solo lugar.
const thClass =
  'px-4 py-3 text-left font-medium text-slate-600';

/**
 * Props (contrato) de la tabla de clientes. Todos los handlers son
 * callbacks que reciben el cliente involucrado, delegando la decisión.
 *
 * @typedef {Object} ClienteTableProps
 * @property {Cliente[]} clientes - Lista de clientes a renderizar.
 * @property {(cliente: Cliente) => void} onEdit - Abre el form en modo edición.
 * @property {(cliente: Cliente) => void} onDelete - Confirma y elimina.
 * @property {(cliente: Cliente) => void} onView - Abre el detalle del cliente.
 * @property {(cliente: Cliente) => void} onAssignObra - Abre el modal de asignación.
 */
type ClienteTableProps = {
  clientes: Cliente[];
  onEdit: (cliente: Cliente) => void;
  onDelete: (cliente: Cliente) => void;
  onView: (cliente: Cliente) => void;
  onAssignObra: (cliente: Cliente) => void;
};

/**
 * Tabla de clientes con ordenamiento por columna.
 *
 * @param {ClienteTableProps} props - Ver definición del contrato.
 * @returns {JSX.Element} El <table> con las filas de clientes ordenadas.
 */
export default function ClienteTable ({
  clientes,
  onEdit,
  onDelete,
  onView,
  onAssignObra,
}: ClienteTableProps) {
  // El rol del usuario logueado decide qué botones de acción se muestran.
  const { isAdmin } = useAuth();

  // useSort con columna inicial "nombre". Sin getValue → accede directo a
  // la propiedad del cliente por nombre de columna (cliente[sortKey]).
  const { sortedData, sortKey, sortDir, toggleSort } = useSort(clientes, 'nombre');

  return (
    <div className='overflow-x-auto rounded-lg border border-slate-200'>
      <table className='min-w-full divide-y divide-slate-200 text-sm'>
        <thead className='bg-slate-50'>
          <tr>
            {/* Cada encabezado es CLICKEABLE: onClick dispara toggleSort
                e `indicator(col)` agrega la flecha ▲/▼ de la columna activa */}
            <SortableTh clave='nombre' sortKey={sortKey} sortDir={sortDir} onSort={toggleSort} className={thClass}>Nombre</SortableTh>
            <SortableTh clave='correoElectronico' sortKey={sortKey} sortDir={sortDir} onSort={toggleSort} className={thClass}>Email</SortableTh>
            <SortableTh clave='cuit' sortKey={sortKey} sortDir={sortDir} onSort={toggleSort} className={thClass}>CUIT</SortableTh>
            <SortableTh clave='maximoDescubierto' sortKey={sortKey} sortDir={sortDir} onSort={toggleSort} className={thClass}>Desc. Máximo</SortableTh>
            <th className='px-4 py-3 text-right font-medium text-slate-600'>
              Acciones
            </th>
          </tr>
        </thead>
        <tbody className='divide-y divide-slate-200'>
          {sortedData.length === 0
            ? (
              // Fila de "vacío": colSpan={5} hace que la celda ocupe las 5
              // columnas para centrar el mensaje horizontalmente.
              <tr>
                <td colSpan={5} className='px-4 py-8 text-center text-slate-500'>
                  No hay clientes registrados.
                </td>
              </tr>
              )
            : (
                // `sortedData.map(...)`: render de listas → un <tr> por
                // cliente. `key` (única) ayuda a React a identificar qué fila
                // cambió al re-renderizar (eficiencia del diffing).
                sortedData.map((cliente) => (
                  <tr key={cliente.id} className='hover:bg-slate-50'>
                    <td className='px-4 py-3 font-medium text-slate-900'>
                      {cliente.nombre}
                    </td>
                    <td className='px-4 py-3 text-slate-600'>
                      {cliente.correoElectronico}
                    </td>
                    {/* `??` (nullish coalescing): si cuit es null/undefined,
                        muestra "—" en vez de un vacío incómodo */}
                    <td className='px-4 py-3 text-slate-600'>
                      {cliente.cuit ?? '—'}
                    </td>
                    <td className='px-4 py-3 text-slate-600'>
                      {/* `!= null` cubre null y undefined pero NO el 0 (que
                          es un descubierto válido). toLocaleString('es-AR')
                          agrega separador de miles: 1234567 → 1.234.567. */}
                      {cliente.maximoDescubierto != null
                        ? `$${cliente.maximoDescubierto.toLocaleString('es-AR')}`
                        : '—'}
                    </td>
                    <td className='px-4 py-3 text-right'>
                      <div className='flex justify-end gap-2'>
                        {/* "Ver" para todos los roles */}
                        <Button variant='secondary' onClick={() => onView(cliente)}>
                          Ver
                        </Button>
                        {/* `&&` como render condicional DEVUELVE el botón si
                            isAdmin es true, o false (que React ignora al pintar).
                            De este modo el frontend oculta acciones, aunque la
                            seguridad real la garantiza el backend con el JWT. */}
                        {isAdmin && (
                          <Button variant='secondary' onClick={() => onAssignObra(cliente)}>
                            Asignar obra
                          </Button>
                        )}
                        {isAdmin && (
                          <Button variant='secondary' onClick={() => onEdit(cliente)}>
                            Editar
                          </Button>
                        )}
                        {isAdmin && (
                          <Button variant='danger' onClick={() => onDelete(cliente)}>
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
