// ==============================================================
// MODAL "ASIGNAR OBRA A CLIENTE"
// --------------------------------------------------------------
// Igual que AsignarClienteModal pero a la inversa: carga las obras que
// NO están asignadas (obraService.getSinAsignar) y deja elegir una.
// La relación se crea con el MISMO endpoint del otro modal
// (obraService.asignarCliente), pero intercambiando el orden de los ids:
// acá el id de OBRA es el que se elige y el CLIENTE es el fijo.
//
// RESTRICCIÓN DE NEGOCIO: solo se listan obras "sin asignar" — una obra
// ya vinculada a otro cliente no puede estar en dos clientes a la vez.
// ==============================================================

'use client';

import { useEffect, useState } from 'react';
import type { Cliente } from '@/services/clienteService';
import type { Obra } from '@/services/obraService';
import { obraService } from '@/services/obraService';
import Button from './Button';
import Modal from './Modal';
import { getErrorMessage } from '@/lib/http/api-error';

/**
 * PROPS del modal de asignación de obra a cliente.
 *
 * @typedef {Object} AsignarObraModalProps
 * @property {Cliente} cliente - El cliente al que se le asigna la obra
 *   (muestra su nombre en el título).
 * @property {() => void} onAssign - Callback al asignar (padre refresca).
 * @property {() => void} onClose - Cierra el modal.
 */
type AsignarObraModalProps = {
  cliente: Cliente;
  onAssign: () => void;
  onClose: () => void;
};

/**
 * Modal que carga las obras sin asignar y permite vincular una al cliente.
 *
 * Estados UI análogos a AsignarClienteModal: loading / error / selección
 * / submitting, con la cascada de ternarios para los 4 estados de la lista.
 *
 * @param {AsignarObraModalProps} props - Ver AsignarObraModalProps.
 * @returns {JSX.Element} El modal con la lista de obras en radio buttons.
 */
export default function AsignarObraModal ({
  cliente,
  onAssign,
  onClose,
}: AsignarObraModalProps) {
  // Catálogo de obras disponibles para asignar.
  const [obras, setObras] = useState<Obra[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedObraId, setSelectedObraId] = useState<number | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  // Al abrir, obtiene obras sin asignar (una sola vez).
  // IIFE async: `(async () => {...})()` nos da await dentro de useEffect
  // (que no admite devolver funciones async directamente).
  useEffect(() => {
    (async () => {
      try {
        const data = await obraService.getSinAsignar();
        setObras(data);
      } catch (err) {
        setError(getErrorMessage(err, 'No se pudieron cargar las obras disponibles.'));
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  /**
   * Vincula la obra elegida al cliente vía API y cierra el modal.
   */
  const handleAssign = async () => {
    // Guard de seguridad (el botón ya viene disabled, pero por si acaso).
    if (selectedObraId == null) return;
    setSubmitting(true);
    setError('');
    try {
      // Ojo con el ORDEN de los args: acá la OBRA es el id elegido y el
      // CLIENTE es el fijo de la prop (relación inversa al otro modal).
      await obraService.asignarCliente(selectedObraId, cliente.id!);
      onAssign(); // el padre refresca las obras del cliente
      onClose();  // y cierra este modal
    } catch (err) {
      setError(getErrorMessage(err, 'No se pudo asignar la obra.'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    // Overlay de modal: fondo oscuro + caja blanca centrada.
    <Modal titulo={<>Asignar obra a {cliente.nombre}</>} onClose={onClose} mostrarCerrar>

      {/* CASCADA DE ESTADOS: cargando → error sin obras → sin obras → lista */}
      {loading
        ? (
          <p className='text-sm text-slate-500'>Cargando obras disponibles…</p>
          )
        : error && obras.length === 0
          ? (
            <p className='text-sm text-red-600'>{error}</p>
            )
          : obras.length === 0
            ? (
              <p className='text-sm text-slate-500'>
                No hay obras disponibles para asignar.
              </p>
              )
            : (
          // Lista de obras con radio button para seleccionar una
              <div className='space-y-2'>
                {obras.map((obra) => (
                  <label
                    key={obra.id}
                    className={`flex cursor-pointer items-center gap-3 rounded-md border p-3 text-sm transition ${
                  selectedObraId === obra.id
                    ? 'border-slate-900 bg-slate-50'
                    : 'border-slate-200 hover:bg-slate-50'
                }`}
                  >
                    {/* Radio controlled: checked sale del estado y el cambio
                          elige la obra. name='obra' agrupa los radios. */}
                    <input
                      type='radio'
                      name='obra'
                      value={obra.id}
                      checked={selectedObraId === obra.id}
                      onChange={() => setSelectedObraId(obra.id ?? null)}
                      className='accent-slate-900'
                    />
                    <div>
                      <p className='font-medium text-slate-900'>{obra.direccion}</p>
                      <p className='text-slate-500'>
                        Presupuesto: ${obra.presupuesto.toLocaleString('es-AR')}
                      </p>
                    </div>
                  </label>
                ))}
              </div>
              )}

      {/* Error de asignación (cuando ya había obras cargadas) */}
      {error && obras.length > 0 && (
        <p className='mt-2 text-sm text-red-600'>{error}</p>
      )}

      <div className='mt-6 flex justify-end gap-2'>
        <Button variant='secondary' onClick={onClose} disabled={submitting}>
          Cancelar
        </Button>
        <Button
          onClick={handleAssign}
          disabled={selectedObraId == null || submitting}
        >
          {submitting ? 'Asignando…' : 'Asignar'}
        </Button>
      </div>
    </Modal>
  );
}
