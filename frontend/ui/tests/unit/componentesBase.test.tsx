import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import Field from '@/components/Field';
import { FeedbackProvider, useFeedback } from '@/components/Feedback';
import Modal from '@/components/Modal';

function Abridor ({ onClose = vi.fn() }: { onClose?: () => void }) {
  const [abierto, setAbierto] = useState(false);
  return (
    <>
      <button onClick={() => setAbierto(true)}>Abrir</button>
      {abierto && (
        <Modal titulo='Nuevo cliente' onClose={() => { onClose(); setAbierto(false); }}>
          <input aria-label='Nombre' />
          <button>Guardar</button>
        </Modal>
      )}
    </>
  );
}

describe('Modal', () => {
  it('es un dialogo modal con nombre accesible', async () => {
    render(<Abridor />);
    await userEvent.click(screen.getByRole('button', { name: 'Abrir' }));

    const dialogo = screen.getByRole('dialog', { name: 'Nuevo cliente' });
    expect(dialogo).toHaveAttribute('aria-modal', 'true');
  });

  it('pone el foco en el primer campo al abrir', async () => {
    render(<Abridor />);
    await userEvent.click(screen.getByRole('button', { name: 'Abrir' }));

    expect(screen.getByRole('textbox', { name: 'Nombre' })).toHaveFocus();
  });

  it('Esc lo cierra y el foco vuelve al boton que lo abrio', async () => {
    const onClose = vi.fn();
    render(<Abridor onClose={onClose} />);
    const abrir = screen.getByRole('button', { name: 'Abrir' });
    await userEvent.click(abrir);

    await userEvent.keyboard('{Escape}');

    expect(onClose).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(abrir).toHaveFocus();
  });

  it('Tab no saca el foco del dialogo (da la vuelta)', async () => {
    render(<Abridor />);
    await userEvent.click(screen.getByRole('button', { name: 'Abrir' }));
    expect(screen.getByRole('textbox', { name: 'Nombre' })).toHaveFocus();

    await userEvent.tab();
    expect(screen.getByRole('button', { name: 'Guardar' })).toHaveFocus();
    await userEvent.tab();
    expect(screen.getByRole('textbox', { name: 'Nombre' })).toHaveFocus();
    await userEvent.tab({ shift: true });
    expect(screen.getByRole('button', { name: 'Guardar' })).toHaveFocus();
  });

  it('mostrarCerrar agrega un boton "Cerrar" que llama a onClose, y el foco inicial no es ese boton', async () => {
    const onClose = vi.fn();
    render(
      <Modal titulo='Detalle' onClose={onClose} mostrarCerrar>
        <input aria-label='Dato' />
      </Modal>
    );

    expect(screen.getByRole('textbox', { name: 'Dato' })).toHaveFocus();
    await userEvent.click(screen.getByRole('button', { name: 'Cerrar' }));
    expect(onClose).toHaveBeenCalled();
  });

  it('un modal sin campos enfocables toma el foco el mismo (Esc igual funciona)', async () => {
    const onClose = vi.fn();
    render(<Modal titulo='Solo texto' onClose={onClose}><p>Hola</p></Modal>);

    expect(screen.getByRole('dialog')).toHaveFocus();
    await userEvent.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalled();
  });
});

describe('Field', () => {
  it('asocia la etiqueta con su control (getByLabelText lo encuentra)', () => {
    render(
      <>
        <Field label='Nombre' required><input /></Field>
        <Field label='Rubro'><select><option>A</option></select></Field>
      </>
    );

    expect(screen.getByLabelText(/Nombre/)).toHaveAttribute('aria-required', 'true');
    expect(screen.getByLabelText('Rubro').tagName).toBe('SELECT');
  });

  it('dos campos con la misma etiqueta no comparten id', () => {
    render(<><Field label='Cantidad'><input /></Field><Field label='Cantidad'><input /></Field></>);

    const [a, b] = screen.getAllByLabelText('Cantidad');
    expect(a.id).not.toBe('');
    expect(a.id).not.toBe(b.id);
  });

  it('un error se muestra y queda asociado al control', () => {
    render(<Field label='Email' error='No es un email'><input /></Field>);

    const campo = screen.getByLabelText('Email');
    expect(campo).toHaveAttribute('aria-invalid', 'true');
    expect(campo).toHaveAccessibleDescription('No es un email');
  });
});

function Probador ({ accion }: { accion: (f: ReturnType<typeof useFeedback>) => void }) {
  const feedback = useFeedback();
  return <button onClick={() => accion(feedback)}>Probar</button>;
}

describe('avisos y confirmacion', () => {
  it('un aviso va a una region aria-live; los errores usan role=alert', async () => {
    const { container } = render(
      <FeedbackProvider>
        <Probador accion={(f) => { f.avisar('ok', 'Guardado'); f.avisar('error', 'No se pudo guardar'); }} />
      </FeedbackProvider>
    );
    expect(container.parentElement!.querySelector('[aria-live="polite"]')).not.toBeNull(); // existe antes del primer aviso

    await userEvent.click(screen.getByRole('button', { name: 'Probar' }));

    const region = document.querySelector('[aria-live="polite"]')!;
    expect(region).toContainElement(screen.getByRole('status'));
    expect(screen.getByRole('status')).toHaveTextContent('Guardado');
    expect(region).toContainElement(screen.getByRole('alert'));
    expect(screen.getByRole('alert')).toHaveTextContent('No se pudo guardar');
  });

  it('los avisos se pueden cerrar y se van solos', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    render(<FeedbackProvider><Probador accion={(f) => { f.avisar('ok', 'Uno'); f.avisar('warn', 'Dos'); }} /></FeedbackProvider>);
    await userEvent.click(screen.getByRole('button', { name: 'Probar' }));

    await userEvent.click(screen.getAllByRole('button', { name: 'Cerrar aviso' })[0]);
    expect(screen.queryByText('Uno')).toBeNull();
    await act(async () => { await vi.advanceTimersByTimeAsync(9000); });
    expect(screen.queryByText('Dos')).toBeNull();
    vi.useRealTimers();
  });

  it('confirmar devuelve true al aceptar y false al cancelar, sin usar window.confirm', async () => {
    const nativo = vi.spyOn(window, 'confirm');
    const resultados: boolean[] = [];
    render(
      <FeedbackProvider>
        <Probador accion={async (f) => { resultados.push(await f.confirmar({ mensaje: '¿Eliminar?', confirmar: 'Eliminar', peligro: true })); }} />
      </FeedbackProvider>
    );

    await userEvent.click(screen.getByRole('button', { name: 'Probar' }));
    expect(screen.getByRole('dialog', { name: 'Confirmar' })).toHaveTextContent('¿Eliminar?');
    await userEvent.click(screen.getByRole('button', { name: 'Eliminar' }));
    await waitFor(() => expect(resultados).toEqual([true]));

    await userEvent.click(screen.getByRole('button', { name: 'Probar' }));
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }));
    await waitFor(() => expect(resultados).toEqual([true, false]));
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(nativo).not.toHaveBeenCalled();
  });

  it('en una accion destructiva el foco inicial esta en Cancelar y Esc cancela', async () => {
    const resultados: boolean[] = [];
    render(
      <FeedbackProvider>
        <Probador accion={async (f) => { resultados.push(await f.confirmar({ mensaje: '¿Seguro?', peligro: true })); }} />
      </FeedbackProvider>
    );
    await userEvent.click(screen.getByRole('button', { name: 'Probar' }));

    expect(screen.getByRole('button', { name: 'Cancelar' })).toHaveFocus();
    await userEvent.keyboard('{Escape}');

    await waitFor(() => expect(resultados).toEqual([false]));
  });
});
