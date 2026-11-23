import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ClientesPageClient from '@/app/(app)/clientes/ClientesPageClient';
import PedidoForm from '@/components/PedidoForm';
import { ApiError } from '@/lib/http/api-error';
import { clienteService } from '@/services/clienteService';
import { productoService } from '@/services/productoService';
import { obraService } from '@/services/obraService';

vi.mock('@/services/clienteService', () => ({ clienteService: { getAll: vi.fn(), delete: vi.fn() } }));
vi.mock('@/services/productoService', () => ({ productoService: { getAll: vi.fn() } }));
vi.mock('@/services/obraService', () => ({ obraService: { getByClienteId: vi.fn() } }));

const usuario = vi.hoisted(() => ({ actual: { email: 'a@a.com', nombre: 'Ana', rol: 'ADMIN' } as Record<string, unknown> }));
vi.mock('@/lib/auth/AuthContext', () => ({
  useAuth: () => ({ user: usuario.actual, isAdmin: usuario.actual.rol === 'ADMIN' }),
}));

const clientes = vi.mocked(clienteService.getAll);
const productos = vi.mocked(productoService.getAll);
const obras = vi.mocked(obraService.getByClienteId);

const cliente = { id: 1, nombre: 'Constructora Sur', correoElectronico: 'sur@mail.com', cuit: '30-1', maximoDescubierto: 1000 };

beforeEach(() => {
  vi.resetAllMocks();
  usuario.actual = { email: 'a@a.com', nombre: 'Ana', rol: 'ADMIN' };
});

describe('pantalla de clientes: carga, error y reintento', () => {
  it('muestra "Cargando…" y despues la tabla', async () => {
    clientes.mockResolvedValue([cliente as never]);

    render(<ClientesPageClient />);

    expect(screen.getByText(/Cargando/)).toBeInTheDocument();
    expect(await screen.findByText('Constructora Sur')).toBeInTheDocument();
    expect(screen.queryByText(/Cargando/)).toBeNull();
  });

  it('si falla muestra el error con Reintentar y al reintentar carga los datos', async () => {
    clientes
      .mockRejectedValueOnce(new ApiError(503, 'El servicio está temporalmente fuera de servicio.'))
      .mockResolvedValueOnce([cliente as never]);

    render(<ClientesPageClient />);

    expect(await screen.findByRole('alert')).toHaveTextContent('El servicio está temporalmente fuera de servicio.');
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('Constructora Sur')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).toBeNull();
    expect(clientes).toHaveBeenCalledTimes(2);
  });

  it('un 403 muestra el mensaje sin boton Reintentar', async () => {
    clientes.mockRejectedValue(new ApiError(403, 'No tenés permisos para realizar esta acción.'));

    render(<ClientesPageClient />);

    expect(await screen.findByRole('alert')).toHaveTextContent('No tenés permisos');
    expect(screen.queryByRole('button', { name: 'Reintentar' })).toBeNull();
    expect(clientes).toHaveBeenCalledTimes(1);
  });

  it('no deja temporizadores de reintento pendientes', async () => {
    vi.useFakeTimers();
    clientes.mockRejectedValue(new ApiError(502, 'caido'));

    render(<ClientesPageClient />);
    await vi.advanceTimersByTimeAsync(30_000);

    expect(clientes).toHaveBeenCalledTimes(1);
    expect(vi.getTimerCount()).toBe(0);
    vi.useRealTimers();
  });
});

describe('formulario de pedido: informa si no pudo cargar los catalogos', () => {
  const producto = { id: 7, nombre: 'Cemento', precio: 100, descuentoPromocional: 0, unidadMedida: 'BOLSA', stockActual: 10 };

  it('si fallan los clientes o productos lo avisa y permite reintentar', async () => {
    clientes.mockRejectedValueOnce(new ApiError(502, 'El servidor no está disponible en este momento.'));
    productos.mockResolvedValue([producto as never]);
    clientes.mockResolvedValueOnce([cliente as never]);

    render(<PedidoForm onSubmit={vi.fn()} onCancel={vi.fn()} />);

    expect(await screen.findByRole('alert')).toHaveTextContent('El servidor no está disponible');
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }));

    await waitFor(() => expect(screen.queryByRole('alert')).toBeNull());
    expect(screen.getByRole('option', { name: 'Constructora Sur' })).toBeInTheDocument();
  });

  it('un CLIENTE no pide la lista de clientes', async () => {
    usuario.actual = { email: 'c@c.com', nombre: 'Cli', rol: 'CLIENTE', clienteId: 1, clienteNombre: 'Constructora Sur' };
    productos.mockResolvedValue([producto as never]);
    obras.mockResolvedValue([]);

    render(<PedidoForm onSubmit={vi.fn()} onCancel={vi.fn()} />);

    await waitFor(() => expect(productos).toHaveBeenCalled());
    expect(clientes).not.toHaveBeenCalled();
  });

  it('si fallan las obras del cliente lo avisa junto al selector', async () => {
    clientes.mockResolvedValue([cliente as never]);
    productos.mockResolvedValue([]);
    obras.mockRejectedValue(new ApiError(503, 'El servicio está temporalmente fuera de servicio.'));

    render(<PedidoForm onSubmit={vi.fn()} onCancel={vi.fn()} />);
    await screen.findByRole('option', { name: 'Constructora Sur' });
    await userEvent.selectOptions(screen.getAllByRole('combobox')[0], '1');

    expect(await screen.findByText('El servicio está temporalmente fuera de servicio.')).toBeInTheDocument();
  });
});
