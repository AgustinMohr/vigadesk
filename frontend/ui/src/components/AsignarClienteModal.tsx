// ==============================================================
// MODAL "ASIGNAR CLIENTE A OBRA"
// --------------------------------------------------------------
// Mismo patrón de los otros modales con una diferencia: NO usa un
// <select> sino una LISTA de tarjetas con radio buttons para elegir
// el cliente. Al montarse carga los clientes con una IIFE async
// (async/await dentro de un useEffect).
//
// PATRÓN IIFE ASYNC EN useEffect: `(async () => { ... })()` — se define
// una función async y se EJECUTA inmediatamente. Por qué: useEffect no
// acepta funciones async directamente (devolver una promesa rompería el
// cleanup). Envolver en IIFE nos deja usar `await` de forma natural.
//
// COMPORTAMIENTO DE BOTONES: el botón "Asignar" está deshabilitado
// (disabled) mientras no haya un cliente seleccionado O mientras se está
// enviando (evita doble submit). El flujo exitoso llama a onAssign()
// (el padre refresca la lista) y a onClose() (cierra este modal).
// ==============================================================

'use client';

import { useEffect, useState } from 'react';
import type { Cliente } from '@/services/clienteService';
import { clienteService } from '@/services/clienteService';
import type { Obra } from '@/services/obraService';
import { obraService } from '@/services/obraService';
import Button from './Button';
import Modal from './Modal';
import { getErrorMessage } from '@/lib/http/api-error';

/**
 * PROPS del modal de asignación de cliente a obra.
 *
 * @typedef {Object} AsignarClienteModalProps
 * @property {Obra} obra - La obra que se está asignando (muestra su dirección).
 * @property {() => void} onAssign - Callback al asignar exitosamente (el padre
 *   refresca las obras para reflejar la nueva relación).
 * @property {() => void} onClose - Callback para cerrar el modal.
 */
type AsignarClienteModalProps = {
  obra: Obra;
  onAssign: () => void;
  onClose: () => void;
};

/**
 * Modal que carga los clientes disponibles y permite asociar uno a la obra.
 *
 * Estados del contrato UI: `loading` (while fetch), `error` (si falla),
 * `selectedClienteId` (radio chequeado) y `submitting` (while assign).
 *
 * @param {AsignarClienteModalProps} props - Ver AsignarClienteModalProps.
 * @returns {JSX.Element} El modal con la lista de clientes en radio buttons.
 */
export default function AsignarClienteModal ({
  obra,
  onAssign,
  onClose,
}: AsignarClienteModalProps) {
  // Catálogo de clientes disponibles.
  const [clientes, setClientes] = useState<Cliente[]>([]);
  const [loading, setLoading] = useState(true);
  // `number | null`: null = ninguno seleccionado todavía.
  const [selectedClienteId, setSelectedClienteId] = useState<number | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  // Al abrir, obtiene los clientes activos (una sola vez, [] de deps).
  useEffect(() => {
    (async () => {
      try {
        // `await` pausa la IIFE hasta que la promesa del service resuelva.
        const data = await clienteService.getAll();
        setClientes(data);
      } catch (err) {
        setError(getErrorMessage(err, 'No se pudieron cargar los clientes.'));
      } finally {
        // `finally` corre SIEMPRE: con éxito, el loading termina igual.
        setLoading(false);
      }
    })();
  }, []);

  /**
   * Asigna el cliente seleccionado a la obra vía API y cierra el modal.
   */
  const handleAssign = async () => {
    // Guard: si no hay selección, no hace nada (aunque el botón ya está
    // deshabilitado, este check protege llamadas programáticas).
    if (selectedClienteId == null) return;
    setSubmitting(true);
    setError('');
    try {
      // `obra.id!` — non-null assertion: la obra siempre trae id al editar.
      await obraService.asignarCliente(obra.id!, selectedClienteId);
      onAssign(); // el padre refresca la lista de obras
      onClose();  // y cierra este modal
    } catch (err) {
      setError(getErrorMessage(err, 'No se pudo asignar el cliente a la obra.'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    // Overlay de modal: fondo oscuro + caja blanca centrada.
    <Modal titulo='Asignar cliente a obra' onClose={onClose} mostrarCerrar>

      {/* El contexto de la acción: la dirección de la obra fija */}
      <p className='mb-3 text-sm text-slate-600'>{obra.direccion}</p>

      {/* CASCADA DE ESTADOS con ternarios anidados:
            1. Cargando → mensaje gris.
            2. Error SIN clientes → mensaje rojo (no hay nada para elegir).
            3. Sin clientes en total → mensaje informativo.
            4. Hay clientes → la lista con radio buttons. */}
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
                  // Un <label> envuelve el radio + la info: así, al hacer
                  // click en CUALQUIER parte de la tarjeta, se activa el
                  // radio (mejor UX de objetivo de click más grande).
                  <label
                    key={cliente.id}
                    className={`flex cursor-pointer items-center gap-3 rounded-md border p-3 text-sm transition ${
                  // Ternario de clases: la tarjeta seleccionada se resalta
                  // (borde oscuro + fondo gris claro).
                  selectedClienteId === cliente.id
                    ? 'border-slate-900 bg-slate-50'
                    : 'border-slate-200 hover:bg-slate-50'
                }`}
                  >
                    {/* Radio CONTROLLED: `checked` sale del estado; al
                          cambiar, marca ese cliente como seleccionado. */}
                    <input
                      type='radio'
                      name='cliente' // nombre común → los radios se excluyen entre sí
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

      {/* Segundo bloque de error: si hubo error pero IGUAL hay clientes
            (carga vieja + error de asignación), se muestra acá abajo. */}
      {error && clientes.length > 0 && (
        <p className='mt-2 text-sm text-red-600'>{error}</p>
      )}

      <div className='mt-6 flex justify-end gap-2'>
        <Button variant='secondary' onClick={onClose} disabled={submitting}>
          Cancelar
        </Button>
        {/* Botón principal: deshabilitado sin selección o mientras envía */}
        <Button
          onClick={handleAssign}
          disabled={selectedClienteId == null || submitting}
        >
          {submitting ? 'Asignando…' : 'Asignar'}
        </Button>
      </div>
    </Modal>
  );
}
