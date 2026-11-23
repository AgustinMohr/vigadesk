'use client';

import { cloneElement, useId, type ReactElement, type ReactNode } from 'react';

type FieldProps = {
  label: ReactNode;
  /** Marca el campo como obligatorio (asterisco visible y aria-required). */
  required?: boolean;
  /** Mensaje de error del campo; se asocia con aria-describedby. */
  error?: string;
  /** Clases del <label> cuando no es el estilo por defecto. */
  labelClassName?: string;
  /** El control (input, select o textarea). Field le pone el `id` que lo asocia con la etiqueta. */
  children: ReactElement<{ id?: string; 'aria-required'?: boolean; 'aria-invalid'?: boolean; 'aria-describedby'?: string }>;
};

/** Etiqueta + control con `htmlFor`/`id` generados con useId, para que el campo tenga nombre accesible. */
export default function Field ({ label, required, error, labelClassName = 'block text-sm font-medium text-slate-700', children }: FieldProps) {
  const id = useId();
  const errorId = `${id}-error`;
  return (
    <div>
      <label htmlFor={id} className={labelClassName}>
        {label}
        {required && <span className='text-red-500' aria-hidden='true'> *</span>}
      </label>
      {cloneElement(children, {
        id,
        'aria-required': required || undefined,
        'aria-invalid': error ? true : undefined,
        'aria-describedby': error ? errorId : undefined,
      })}
      {error && <p id={errorId} className='mt-1 text-sm text-red-600'>{error}</p>}
    </div>
  );
}
