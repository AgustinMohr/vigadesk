// ==============================================================
// MODAL DE DETALLE DE CLIENTE
// --------------------------------------------------------------
// Ventana de SOLO LECTURA (no hay formulario ni edición): muestra los
// datos del cliente en la primera mitad y, en la segunda, carga y lista
// sus obras asociadas al abrirse.
//
// useEffect CON DEPENDENCIA DINÁMICA: a diferencia de los otros modales
// (deps = []), acá la dependencia es `[cliente.id]`. El efecto corre al
// montar (primera vez) y se REPITE si `cliente.id` cambia — por ejemplo
// si el padre re-abre el modal con otro cliente. Es el patrón correcto
// para datos que dependen de un parámetro.
// ==============================================================

'use client';

import { useEffect, useState } from 'react';
import type { Cliente } from '@/services/clienteService';
import type { Obra } from '@/services/obraService';
import { obraService } from '@/services/obraService';
import Button from './Button';
import Modal from './Modal';

/**
 * PROPS del modal de detalle.
 *
 * @typedef {Object} ClienteDetailModalProps
 * @property {Cliente} cliente - El cliente a mostrar en detalle.
 * @property {() => void} onClose - Cierra el modal.
 */
type ClienteDetailModalProps = {
  cliente: Cliente;
  onClose: () => void;
};

/**
 * Modal de solo lectura con los datos del cliente y sus obras asignadas.
 *
 * No persiste nada: su único propósito es informar. Por eso no tiene
 * estado `submitting` y el único botón es "Cerrar".
 *
 * @param {ClienteDetailModalProps} props - Ver ClienteDetailModalProps.
 * @returns {JSX.Element} El modal de detalle del cliente.
 */
export default function ClienteDetailModal ({
  cliente,
  onClose,
}: ClienteDetailModalProps) {
  // Obras del cliente, traídas del backend.
  const [obras, setObras] = useState<Obra[]>([]);
  const [loadingObras, setLoadingObras] = useState(true);

  // Obtiene obras del cliente al abrir el modal.
  // Guard temprano: si cliente.id es null, no llamamos a la API (evita
  // un request a "/by-cliente/null" y un setState en un id inexistente).
  useEffect(() => {
    if (cliente.id == null) return;

    // IIFE async dentro de useEffect (para poder usar await).
    (async () => {
      try {
        const data = await obraService.getByClienteId(cliente.id!);
        setObras(data);
      } catch {
        // Si falla, la sección de obras queda vacía con su mensaje; no
        // matamos el resto del detalle (degradación controlada).
        setObras([]);
      } finally {
        setLoadingObras(false);
      }
    })();
  }, [cliente.id]); // si cambia el cliente, re-carga sus obras

  return (
    // Overlay de modal: fondo oscuro + caja blanca centrada.
    <Modal titulo='Detalle del cliente' onClose={onClose} ancho='max-w-lg' mostrarCerrar>

      {/* Datos fijos del cliente (de la entidad ya cargada por el padre,
            no necesita fetch adicional). Los `?? '—'` cubren campos opcionales. */}
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
          {/* `!= null` acepta 0 como valor válido; toLocaleString formatea */}
          <p className='text-slate-900'>
            {cliente.maximoDescubierto != null
              ? `$${cliente.maximoDescubierto.toLocaleString('es-AR')}`
              : '—'}
          </p>
        </div>
      </div>

      <hr className='my-4 border-slate-200' />

      {/* Segunda mitad: obras asociadas, cargadas en el useEffect */}
      <h4 className='mb-3 text-sm font-semibold text-slate-700'>
        Obras asignadas
      </h4>

      {loadingObras
        ? (
          <p className='text-sm text-slate-500'>Cargando obras…</p>
          )
        : obras.length === 0
          ? (
            <p className='text-sm text-slate-500'>Este cliente no tiene obras asignadas.</p>
            )
          : (
            <div className='space-y-2'>
              {/* Una tarjeta por obra del cliente */}
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
                    {/* `esRemodelacion` es booleano → ternario para texto */}
                    <span>
                      {obra.esRemodelacion ? 'Remodelación' : 'Obra nueva'}
                    </span>
                  </div>
                </div>
              ))}
            </div>
            )}

      <div className='mt-6 flex justify-end'>
        {/* Único botón: cerrar. No hay acciones que puedan fallar acá. */}
        <Button variant='secondary' onClick={onClose}>
          Cerrar
        </Button>
      </div>
    </Modal>
  );
}
