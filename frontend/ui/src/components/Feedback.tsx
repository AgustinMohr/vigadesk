'use client';

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import Modal from './Modal';

// ==============================================================
// Avisos (toasts) y confirmaciones: reemplazan a alert() y window.confirm().
//  - Los avisos van a una región aria-live, que existe siempre en pantalla, para que los lectores de
//    pantalla los anuncien. Los errores usan role="alert" (se leen enseguida).
//  - La confirmación es un diálogo accesible (Modal) que devuelve una promesa con la decisión.
// ==============================================================

export type TipoAviso = 'ok' | 'warn' | 'error';

type Aviso = { id: number; tipo: TipoAviso; texto: string };

type OpcionesConfirmacion = {
  titulo?: string;
  mensaje: string;
  /** Texto del botón de confirmar. */
  confirmar?: string;
  /** Acción destructiva: el botón de confirmar va en rojo y el foco inicial queda en "Cancelar". */
  peligro?: boolean;
};

type FeedbackValue = {
  avisar: (tipo: TipoAviso, texto: string) => void;
  confirmar: (opciones: OpcionesConfirmacion) => Promise<boolean>;
};

const FeedbackContext = createContext<FeedbackValue | null>(null);

const clasesAviso: Record<TipoAviso, string> = {
  ok: 'border-green-300 bg-green-50 text-green-800',
  warn: 'border-amber-300 bg-amber-50 text-amber-800',
  error: 'border-red-300 bg-red-50 text-red-800',
};

const DURACION_MS = 8000;

export function FeedbackProvider ({ children }: { children: ReactNode }) {
  const [avisos, setAvisos] = useState<Aviso[]>([]);
  const [pendiente, setPendiente] = useState<{ opciones: OpcionesConfirmacion; resolver: (v: boolean) => void } | null>(null);
  const siguiente = useRef(1);

  const quitar = useCallback((id: number) => setAvisos((lista) => lista.filter((a) => a.id !== id)), []);

  const avisar = useCallback((tipo: TipoAviso, texto: string) => {
    const id = siguiente.current++;
    setAvisos((lista) => [...lista, { id, tipo, texto }]);
    setTimeout(() => quitar(id), DURACION_MS);
  }, [quitar]);

  const confirmar = useCallback((opciones: OpcionesConfirmacion) =>
    new Promise<boolean>((resolve) => setPendiente({ opciones, resolver: resolve })), []);

  const decidir = (valor: boolean) => {
    pendiente?.resolver(valor);
    setPendiente(null);
  };

  const valor = useMemo(() => ({ avisar, confirmar }), [avisar, confirmar]);

  return (
    <FeedbackContext.Provider value={valor}>
      {children}

      {/* Región en vivo: siempre montada. Cada aviso es un mensaje nuevo dentro de ella. */}
      <div aria-live='polite' className='pointer-events-none fixed bottom-4 right-4 z-[60] flex w-80 max-w-[calc(100vw-2rem)] flex-col gap-2'>
        {avisos.map((a) => (
          <div
            key={a.id}
            role={a.tipo === 'error' ? 'alert' : 'status'}
            className={`pointer-events-auto flex items-start gap-2 rounded border px-3 py-2 text-sm shadow ${clasesAviso[a.tipo]}`}
          >
            <p className='flex-1'>{a.texto}</p>
            <button type='button' aria-label='Cerrar aviso' onClick={() => quitar(a.id)} className='leading-none opacity-60 hover:opacity-100'>
              &times;
            </button>
          </div>
        ))}
      </div>

      {pendiente && (
        <ConfirmDialog
          opciones={pendiente.opciones}
          onConfirm={() => decidir(true)}
          onCancel={() => decidir(false)}
        />
      )}
    </FeedbackContext.Provider>
  );
}

/** Diálogo de confirmación: Esc cancela; en acciones destructivas el foco inicial está en "Cancelar". */
export function ConfirmDialog ({ opciones, onConfirm, onCancel }: { opciones: OpcionesConfirmacion; onConfirm: () => void; onCancel: () => void }) {
  const cancelar = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    if (opciones.peligro) cancelar.current?.focus();
  }, [opciones.peligro]);

  return (
    <Modal titulo={opciones.titulo ?? 'Confirmar'} onClose={onCancel} ancho='max-w-sm'>
      <p className='mb-4 text-sm text-slate-700'>{opciones.mensaje}</p>
      <div className='flex justify-end gap-2'>
        <button
          ref={cancelar}
          type='button'
          onClick={onCancel}
          className='rounded border border-slate-300 px-3 py-2 text-sm text-slate-700 hover:bg-slate-100'
        >
          Cancelar
        </button>
        <button
          type='button'
          onClick={onConfirm}
          className={`rounded px-3 py-2 text-sm text-white ${opciones.peligro ? 'bg-red-600 hover:bg-red-700' : 'bg-slate-800 hover:bg-slate-900'}`}
        >
          {opciones.confirmar ?? 'Aceptar'}
        </button>
      </div>
    </Modal>
  );
}

export function useFeedback (): FeedbackValue {
  const ctx = useContext(FeedbackContext);
  if (!ctx) throw new Error('useFeedback debe usarse dentro de un FeedbackProvider');
  return ctx;
}
