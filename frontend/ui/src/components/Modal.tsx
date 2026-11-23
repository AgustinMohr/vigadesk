'use client';

import { useEffect, useId, useRef, type KeyboardEvent, type ReactNode } from 'react';

type ModalProps = {
  /** Título visible; también es el nombre accesible del diálogo (aria-labelledby). */
  titulo: ReactNode;
  /** Cierra el modal: se llama al apretar Esc y al usar el botón "×". */
  onClose: () => void;
  /** Ancho máximo del panel (clase de Tailwind). */
  ancho?: string;
  /** Clases extra del panel (por ejemplo, scroll interno). */
  className?: string;
  /** Muestra el botón "×" junto al título. */
  mostrarCerrar?: boolean;
  children: ReactNode;
};

const FOCUSABLES = 'a[href], button:not([disabled]), input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * Modal accesible: `role="dialog"` con `aria-modal`, nombre por su título, se cierra con Esc, mueve el foco
 * al primer campo al abrir, lo mantiene dentro mientras está abierto (Tab y Shift+Tab) y lo devuelve al
 * elemento que lo abrió al cerrar. Es el único lugar con el overlay (`fixed inset-0`) de la aplicación.
 */
export default function Modal ({ titulo, onClose, ancho = 'max-w-md', className = '', mostrarCerrar = false, children }: ModalProps) {
  const tituloId = useId();
  const panel = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const abrioElFoco = document.activeElement as HTMLElement | null;
    const elementos = panel.current?.querySelectorAll<HTMLElement>(FOCUSABLES);
    // El primer campo del formulario; si no hay, el panel mismo (para que Esc funcione igual).
    const inicial = Array.from(elementos ?? []).find((el) => !el.hasAttribute('data-cerrar')) ?? panel.current;
    inicial?.focus();
    return () => { abrioElFoco?.focus?.(); };
  }, []);

  const alPresionar = (e: KeyboardEvent<HTMLDivElement>) => {
    if (e.key === 'Escape') {
      e.stopPropagation();
      onClose();
      return;
    }
    if (e.key !== 'Tab') return;
    const elementos = Array.from(panel.current?.querySelectorAll<HTMLElement>(FOCUSABLES) ?? []);
    if (elementos.length === 0) {
      e.preventDefault();
      return;
    }
    const primero = elementos[0];
    const ultimo = elementos[elementos.length - 1];
    const activo = document.activeElement;
    if (e.shiftKey && (activo === primero || activo === panel.current)) {
      e.preventDefault();
      ultimo.focus();
    } else if (!e.shiftKey && activo === ultimo) {
      e.preventDefault();
      primero.focus();
    }
  };

  return (
    <div className='fixed inset-0 z-50 flex items-center justify-center bg-black/40'>
      <div
        ref={panel}
        role='dialog'
        aria-modal='true'
        aria-labelledby={tituloId}
        tabIndex={-1}
        onKeyDown={alPresionar}
        className={`w-full ${ancho} rounded-lg bg-white p-6 shadow-xl focus:outline-none ${className}`}
      >
        <div className='mb-4 flex items-center justify-between'>
          <h3 id={tituloId} className='text-lg font-semibold text-slate-900'>{titulo}</h3>
          {mostrarCerrar && (
            <button
              type='button'
              data-cerrar
              aria-label='Cerrar'
              onClick={onClose}
              className='text-xl leading-none text-slate-400 hover:text-slate-600'
            >
              &times;
            </button>
          )}
        </div>
        {children}
      </div>
    </div>
  );
}
