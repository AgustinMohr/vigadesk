// ==============================================================
// FORMULARIO DE USUARIO (modal crear/editar)
// Mismo patrón que ObraForm.tsx. Particularidades:
//  - Recibe un `usuario` opcional: si llega → EDITAR (los campos se
//    inicializan con sus datos); si no → CREAR.
//  - La contraseña es obligatoria al crear; al editar se deja vacía
//    para conservar la existente (se envía `undefined`).
//  - Carga la lista de clientes del <select> al montarse.
// ==============================================================

'use client';

import { useEffect, useState } from 'react';
import type { UsuarioCliente } from '@/services/usuarioService';
import { clienteService, type Cliente } from '@/services/clienteService';
import Button from './Button';

// Modal formulario para crear o editar un usuario de cliente
// La contraseña es obligatoria al crear; al editar se deja vacía para conservarla
type UsuarioFormProps = {
  usuario?: UsuarioCliente;
  onSubmit: (data: Partial<UsuarioCliente>) => Promise<void>;
  onCancel: () => void;
};

export default function UsuarioForm ({
  usuario,
  onSubmit,
  onCancel,
}: UsuarioFormProps) {
  const [clientes, setClientes] = useState<Cliente[]>([]);
  const [nombre, setNombre] = useState(usuario?.nombre ?? '');
  const [email, setEmail] = useState(usuario?.email ?? '');
  const [password, setPassword] = useState('');
  const [clienteId, setClienteId] = useState(usuario?.cliente?.id?.toString() ?? '');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const isEditing = !!usuario;

  useEffect(() => {
    clienteService.getAll()
      .then((data) => setClientes(data))
      .catch(() => {});
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!nombre.trim() || !email.trim()) {
      setError('Nombre y email son obligatorios.');
      return;
    }
    if (!isEditing && !password.trim()) {
      setError('La contraseña es obligatoria al crear el usuario.');
      return;
    }
    const cliente = clientes.find((c) => c.id === Number(clienteId));
    if (!cliente) {
      setError('Debe seleccionar un cliente.');
      return;
    }

    setSubmitting(true);
    try {
      await onSubmit({
        nombre: nombre.trim(),
        email: email.trim(),
        password: password.trim() || undefined,
        cliente: { id: cliente.id!, nombre: cliente.nombre },
      });
    } catch {
      setError('Ocurrió un error al guardar el usuario.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className='fixed inset-0 z-50 flex items-center justify-center bg-black/40'>
      <div className='w-full max-w-md rounded-lg bg-white p-6 shadow-xl'>
        <h3 className='mb-4 text-lg font-semibold text-slate-900'>
          {isEditing ? 'Editar usuario' : 'Nuevo usuario'}
        </h3>

        <form onSubmit={handleSubmit} className='space-y-4'>
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
              Email <span className='text-red-500'>*</span>
            </label>
            <input
              type='email'
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
            />
          </div>

          <div>
            <label className='block text-sm font-medium text-slate-700'>
              Contraseña {isEditing ? '' : <span className='text-red-500'>*</span>}
            </label>
            <input
              type='password'
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder={isEditing ? 'Dejar vacía para conservar' : ''}
              className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
            />
          </div>

          <div>
            <label className='block text-sm font-medium text-slate-700'>
              Cliente <span className='text-red-500'>*</span>
            </label>
            <select
              value={clienteId}
              onChange={(e) => setClienteId(e.target.value)}
              className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
            >
              <option value=''>Seleccionar cliente</option>
              {clientes.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.nombre}
                </option>
              ))}
            </select>
          </div>

          {error && <p className='text-sm text-red-600'>{error}</p>}

          <div className='flex justify-end gap-2 pt-2'>
            <Button variant='secondary' onClick={onCancel} disabled={submitting}>
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
