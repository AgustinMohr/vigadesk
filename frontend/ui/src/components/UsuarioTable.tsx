// ==============================================================
// TABLA DE USUARIOS (componente "tonto" / de presentación)
// --------------------------------------------------------------
// Mismo patrón que ObraTable.tsx. Particularidad: la columna "Cliente"
// no existe en el objeto UsuarioCliente (vive en usuario.cliente.nombre),
// por eso se usa getUsuarioValue (getValue de useSort) para ordenar por
// ese campo anidado.
//
// NOTA DE UX: a diferencia de otras tablas, acá no se usa useAuth: el
// acceso a la pantalla de usuarios ya está restringido a nivel de
// layout/ruteo (la pantalla es adminOnly), así que todos los botones de
// acción se muestran sin condicional de rol.
// ==============================================================

'use client';

import type { UsuarioCliente } from '@/services/usuarioService';
import Button from './Button';
import { useSort } from '@/hooks/useSort';

// Clases CSS de Tailwind para las celdas de encabezado.
const thClass =
  'px-4 py-3 text-left font-medium text-slate-600 cursor-pointer select-none hover:text-slate-900';

/**
 * Props (contrato) de la tabla de usuarios.
 *
 * @typedef {Object} UsuarioTableProps
 * @property {UsuarioCliente[]} usuarios - Lista de usuarios a mostrar.
 * @property {(usuario: UsuarioCliente) => void} onEdit - Abre el form de edición.
 * @property {(usuario: UsuarioCliente) => void} onDelete - Confirma y elimina.
 */
type UsuarioTableProps = {
  usuarios: UsuarioCliente[];
  onEdit: (usuario: UsuarioCliente) => void;
  onDelete: (usuario: UsuarioCliente) => void;
};

/**
 * Función getValue para useSort: traduce cada clave de columna al valor
 * comparable. "clienteNombre" no existe en la raíz: se extrae del objeto
 * anidado `usuario.cliente.nombre`, con fallback "''" si no hay cliente.
 *
 * @param {UsuarioCliente} item - El usuario de la fila.
 * @param {string} key - Clave de la columna a ordenar.
 * @returns {string | number} El valor comparable (nunca undefined).
 */
function getUsuarioValue (item: UsuarioCliente, key: string): string | number {
  if (key === 'nombre') return `${item.nombre} ${item.apellido ?? ''}`.trim();
  if (key === 'email') return item.email;
  if (key === 'clienteNombre') return item.cliente?.nombre ?? '';
  return '';
}

/**
 * Tabla de usuarios de clientes con ordenamiento por columna.
 *
 * @param {UsuarioTableProps} props - Ver definición del contrato.
 * @returns {JSX.Element} El <table> con los usuarios ordenados.
 */
export default function UsuarioTable ({
  usuarios,
  onEdit,
  onDelete,
}: UsuarioTableProps) {
  // useSort con columna inicial "nombre" y getUsuarioValue para los
  // campos anidados (cliente.nombre).
  const { sortedData, toggleSort, indicator } = useSort(
    usuarios,
    'nombre',
    getUsuarioValue
  );

  return (
    <div className='overflow-x-auto rounded-lg border border-slate-200'>
      <table className='min-w-full divide-y divide-slate-200 text-sm'>
        <thead className='bg-slate-50'>
          <tr>
            <th className={thClass} onClick={() => toggleSort('nombre')}>
              Nombre{indicator('nombre')}
            </th>
            <th className={thClass} onClick={() => toggleSort('email')}>
              Email{indicator('email')}
            </th>
            <th className={thClass} onClick={() => toggleSort('clienteNombre')}>
              Cliente{indicator('clienteNombre')}
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
                <td colSpan={4} className='px-4 py-8 text-center text-slate-400'>
                  No hay usuarios registrados.
                </td>
              </tr>
              )
            : (
                sortedData.map((usuario) => (
                  <tr key={usuario.id} className='hover:bg-slate-50'>
                    <td className='px-4 py-3 font-medium text-slate-900'>
                      {usuario.nombre}
                      {usuario.apellido ? ` ${usuario.apellido}` : ''}
                    </td>
                    <td className='px-4 py-3 text-slate-600'>{usuario.email}</td>
                    {/* `usuario.cliente?.nombre ?? '—'`: optional chaining para
                        no romper si no hay cliente, y "—" como placeholder. */}
                    <td className='px-4 py-3 text-slate-600'>
                      {usuario.cliente?.nombre ?? '—'}
                    </td>
                    <td className='px-4 py-3 text-right'>
                      <div className='flex justify-end gap-2'>
                        <Button variant='secondary' onClick={() => onEdit(usuario)}>
                          Editar
                        </Button>
                        <Button variant='danger' onClick={() => onDelete(usuario)}>
                          Eliminar
                        </Button>
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
