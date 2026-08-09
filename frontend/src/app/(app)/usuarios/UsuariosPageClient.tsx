// ==============================================================
// COMPONENTE CLIENTE de /usuarios
// Es la MISMA estructura que ObrasPageClient.tsx (ver ahí la explicación
// de useState/useEffect/useMemo/handlers/modales). Acá el CRUD es de
// usuarios de cliente: nombre, email, y el cliente (empresa) al que
// pertenecen. Solo visible para el admin.
// ==============================================================

'use client';

import { useEffect, useMemo, useState } from 'react';
import { usuarioService, type UsuarioCliente } from '@/services/usuarioService';
import Button from '@/components/Button';
import UsuarioTable from '@/components/UsuarioTable';
import UsuarioForm from '@/components/UsuarioForm';
import { useAuth } from '@/lib/auth/AuthContext';

export default function UsuariosPageClient ({
  initialUsuarios,
}: {
  initialUsuarios: UsuarioCliente[];
}) {
  const [usuarios, setUsuarios] = useState<UsuarioCliente[]>(initialUsuarios);
  const [loading, setLoading] = useState(initialUsuarios.length === 0);
  const [fetchError, setFetchError] = useState('');

  useEffect(() => {
    if (initialUsuarios.length > 0) return;

    let cancelled = false;

    const fetchData = async () => {
      try {
        const data = await usuarioService.getAll();
        if (!cancelled) {
          setUsuarios(data);
          setLoading(false);
        }
      } catch {
        if (!cancelled) setTimeout(fetchData, 5000);
      }
    };

    fetchData();
    return () => { cancelled = true; };
  }, []);

  const [showForm, setShowForm] = useState(false);
  const [selectedUsuario, setSelectedUsuario] = useState<UsuarioCliente | undefined>();
  const [searchTerm, setSearchTerm] = useState('');

  const filteredUsuarios = useMemo(() => {
    if (!searchTerm.trim()) return usuarios;
    const term = searchTerm.toLowerCase().trim();
    return usuarios.filter(
      (u) =>
        u.nombre.toLowerCase().includes(term) ||
        u.email.toLowerCase().includes(term) ||
        (u.cliente?.nombre ?? '').toLowerCase().includes(term)
    );
  }, [usuarios, searchTerm]);

  const { isAdmin } = useAuth();

  const handleCreate = () => {
    setSelectedUsuario(undefined);
    setShowForm(true);
  };

  const handleEdit = (usuario: UsuarioCliente) => {
    setSelectedUsuario(usuario);
    setShowForm(true);
  };

  const refreshUsuarios = async () => {
    try {
      const data = await usuarioService.getAll();
      setUsuarios(data);
    } catch {
      setFetchError('No se pudieron cargar los usuarios.');
    }
  };

  const handleDelete = async (usuario: UsuarioCliente) => {
    if (!window.confirm(`¿Eliminar al usuario "${usuario.nombre}"?`)) return;
    try {
      await usuarioService.delete(usuario.id!);
      await refreshUsuarios();
    } catch {
      alert('No se pudo eliminar el usuario.');
    }
  };

  const handleFormSubmit = async (data: Partial<UsuarioCliente>) => {
    if (selectedUsuario) {
      await usuarioService.update(selectedUsuario.id!, data);
    } else {
      await usuarioService.create(data);
    }
    setShowForm(false);
    setSelectedUsuario(undefined);
    await refreshUsuarios();
  };

  const handleFormCancel = () => {
    setShowForm(false);
    setSelectedUsuario(undefined);
  };

  return (
    <section>
      <div className='flex items-center justify-between'>
        <div>
          <h2 className='text-2xl font-semibold'>Usuarios de clientes</h2>
          <p className='mt-2 text-slate-600'>
            Usuarios que operan en nombre de un cliente (hacen pedidos por su empresa).
          </p>
        </div>
        {isAdmin && <Button onClick={handleCreate}>Nuevo usuario</Button>}
      </div>

      <div className='mt-6'>
        <div className='mb-4 flex items-center gap-3'>
          <input
            type='text'
            placeholder='Buscar por nombre, email o cliente…'
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className='w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
          />
        </div>

        {loading
          ? (
            <p className='text-slate-400'>Cargando usuarios…</p>
            )
          : fetchError
            ? (
              <p className='text-red-600'>{fetchError}</p>
              )
            : (
              <UsuarioTable
                usuarios={filteredUsuarios}
                onEdit={handleEdit}
                onDelete={handleDelete}
              />
              )}
      </div>

      {showForm && (
        <UsuarioForm
          usuario={selectedUsuario}
          onSubmit={handleFormSubmit}
          onCancel={handleFormCancel}
        />
      )}
    </section>
  );
}
