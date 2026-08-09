// ==============================================================
// MODAL DE DETALLE DE CLIENTE
// Ventana de SOLO LECTURA: muestra los datos del cliente y, en la
// segunda mitad, carga y lista sus obras asociadas al abrirse.
// ==============================================================

'use client';

import { useEffect, useState } from 'react';
import type { Cliente } from '@/services/clienteService';
import type { Obra } from '@/services/obraService';
import { obraService } from '@/services/obraService';
import Button from './Button';

// Modal que muestra datos del cliente y carga sus obras asociadas
type ClienteDetailModalProps = {
  cliente: Cliente;
  onClose: () => void;
};

export default function ClienteDetailModal ({
  cliente,
  onClose,
}: ClienteDetailModalProps) {
  const [obras, setObras] = useState<Obra[]>([]);
  const [loadingObras, setLoadingObras] = useState(true);

  // Obtiene obras del cliente al abrir el modal
  useEffect(() => {
    if (cliente.id == null) return;
    (async () => {
      try {
        const data = await obraService.getByClienteId(cliente.id!);
        setObras(data);
      } catch {
        setObras([]);
      } finally {
        setLoadingObras(false);
      }
    })();
  }, [cliente.id]);

  return (
    <div className='fixed inset-0 z-50 flex items-center justify-center bg-black/40'>
      <div className='w-full max-w-lg rounded-lg bg-white p-6 shadow-xl'>
        <div className='mb-4 flex items-center justify-between'>
          <h3 className='text-lg font-semibold text-slate-900'>
            Detalle del cliente
          </h3>
          <button
            onClick={onClose}
            className='text-slate-400 hover:text-slate-600 text-xl leading-none'
          >
            &times;
          </button>
        </div>

        <div className='space-y-3 text-sm'>
          <div>
            <span className='font-medium text-slate-500'>Nombre:</span>
            <p className='text-slate-900'>{cliente.nombre}</p>
          </div>
          <div>
            <span className='font-medium text-slate-500'>Email:</span>
            <p className='text-slate-900'>{cliente.correoElectronico}</p>
          </div>
          <div>
            <span className='font-medium text-slate-500'>CUIT:</span>
            <p className='text-slate-900'>{cliente.cuit ?? '—'}</p>
          </div>
          <div>
            <span className='font-medium text-slate-500'>Descubierto máximo:</span>
            <p className='text-slate-900'>
              {cliente.maximoDescubierto != null
                ? `$${cliente.maximoDescubierto.toLocaleString('es-AR')}`
                : '—'}
            </p>
          </div>
        </div>

        <hr className='my-4 border-slate-200' />

        <h4 className='mb-3 text-sm font-semibold text-slate-700'>
          Obras asignadas
        </h4>

        {loadingObras
          ? (
            <p className='text-sm text-slate-400'>Cargando obras…</p>
            )
          : obras.length === 0
            ? (
              <p className='text-sm text-slate-400'>Este cliente no tiene obras asignadas.</p>
              )
            : (
              <div className='space-y-2'>
                {obras.map((obra) => (
                  <div
                    key={obra.id}
                    className='rounded-md border border-slate-200 bg-slate-50 p-3 text-sm'
                  >
                    <p className='font-medium text-slate-900'>{obra.direccion}</p>
                    <div className='mt-1 flex flex-wrap gap-x-4 text-slate-500'>
                      <span>
                        Presupuesto: ${obra.presupuesto.toLocaleString('es-AR')}
                      </span>
                      <span>
                        {obra.esRemodelacion ? 'Remodelación' : 'Obra nueva'}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
              )}

        <div className='mt-6 flex justify-end'>
          <Button variant='secondary' onClick={onClose}>
            Cerrar
          </Button>
        </div>
      </div>
    </div>
  );
}
