// ==============================================================
// MODAL "ASIGNAR CLIENTE A OBRA"
// Mismo patrón de los otros modales con una diferencia: NO usa un
// <select> sino una LISTA de tarjetas con radio buttons para elegir
// el cliente. Al montarse carga los clientes con una IIFE async
// (async/await dentro de un useEffect).
// ==============================================================

'use client';

import { useEffect, useState } from 'react';
import type { Cliente } from '@/services/clienteService';
import { clienteService } from '@/services/clienteService';
import type { Obra } from '@/services/obraService';
import { obraService } from '@/services/obraService';
import Button from './Button';

// Modal que carga los clientes y permite asociar uno a una obra
type AsignarClienteModalProps = {
  obra: Obra;
  onAssign: () => void;
  onClose: () => void;
};

export default function AsignarClienteModal ({
  obra,
  onAssign,
  onClose,
}: AsignarClienteModalProps) {
  const [clientes, setClientes] = useState<Cliente[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedClienteId, setSelectedClienteId] = useState<number | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  // Al abrir, obtiene los clientes activos
  useEffect(() => {
    (async () => {
      try {
        const data = await clienteService.getAll();
        setClientes(data);
      } catch {
        setError('No se pudieron cargar los clientes.');
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const handleAssign = async () => {
    if (selectedClienteId == null) return;
    setSubmitting(true);
    setError('');
    try {
      await obraService.asignarCliente(obra.id!, selectedClienteId);
      onAssign();
      onClose();
    } catch {
      setError('No se pudo asignar el cliente a la obra.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className='fixed inset-0 z-50 flex items-center justify-center bg-black/40'>
      <div className='w-full max-w-md rounded-lg bg-white p-6 shadow-xl'>
        <div className='mb-4 flex items-center justify-between'>
          <h3 className='text-lg font-semibold text-slate-900'>
            Asignar cliente a obra
          </h3>
          <button
            onClick={onClose}
            className='text-slate-400 hover:text-slate-600 text-xl leading-none'
          >
            &times;
          </button>
        </div>

        <p className='mb-3 text-sm text-slate-600'>{obra.direccion}</p>

        {loading
          ? (
            <p className='text-sm text-slate-400'>Cargando clientes…</p>
            )
          : error && clientes.length === 0
            ? (
              <p className='text-sm text-red-600'>{error}</p>
              )
            : clientes.length === 0
              ? (
                <p className='text-sm text-slate-400'>
                  No hay clientes disponibles para asignar.
                </p>
                )
              : (
                <div className='space-y-2'>
                  {clientes.map((cliente) => (
                    <label
                      key={cliente.id}
                      className={`flex cursor-pointer items-center gap-3 rounded-md border p-3 text-sm transition ${
                  selectedClienteId === cliente.id
                    ? 'border-slate-900 bg-slate-50'
                    : 'border-slate-200 hover:bg-slate-50'
                }`}
                    >
                      <input
                        type='radio'
                        name='cliente'
                        value={cliente.id}
                        checked={selectedClienteId === cliente.id}
                        onChange={() => setSelectedClienteId(cliente.id ?? null)}
                        className='accent-slate-900'
                      />
                      <div>
                        <p className='font-medium text-slate-900'>{cliente.nombre}</p>
                        <p className='text-slate-500'>{cliente.correoElectronico}</p>
                      </div>
                    </label>
                  ))}
                </div>
                )}

        {error && clientes.length > 0 && (
          <p className='mt-2 text-sm text-red-600'>{error}</p>
        )}

        <div className='mt-6 flex justify-end gap-2'>
          <Button variant='secondary' onClick={onClose} disabled={submitting}>
            Cancelar
          </Button>
          <Button
            onClick={handleAssign}
            disabled={selectedClienteId == null || submitting}
          >
            {submitting ? 'Asignando…' : 'Asignar'}
          </Button>
        </div>
      </div>
    </div>
  );
}
