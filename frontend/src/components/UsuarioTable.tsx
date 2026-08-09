// ==============================================================
// TABLA DE USUARIOS
// Mismo patrón que ObraTable.tsx. Particularidad: la columna "Cliente"
// no existe en el objeto UsuarioCliente (vive en usuario.cliente.nombre),
// por eso se usa getUsuarioValue para ordenar por ese campo.
// ==============================================================

'use client';

import type { UsuarioCliente } from '@/services/usuarioService';
import Button from './Button';
import { useSort } from '@/hooks/useSort';

const thClass =
  'px-4 py-3 text-left font-medium text-slate-600 cursor-pointer select-none hover:text-slate-900';

type UsuarioTableProps = {
  usuarios: UsuarioCliente[];
  onEdit: (usuario: UsuarioCliente) => void;
  onDelete: (usuario: UsuarioCliente) => void;
};

function getUsuarioValue (item: UsuarioCliente, key: string): string | number {
  if (key === 'nombre') return item.nombre;
  if (key === 'email') return item.email;
  if (key === 'clienteNombre') return item.cliente?.nombre ?? '';
  return '';
}

export default function UsuarioTable ({
  usuarios,
  onEdit,
  onDelete,
}: UsuarioTableProps) {
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
                    </td>
                    <td className='px-4 py-3 text-slate-600'>{usuario.email}</td>
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
