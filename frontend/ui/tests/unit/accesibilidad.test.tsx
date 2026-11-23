import { render as renderBase, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import axe from 'axe-core';
import type { ReactElement } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ClientesPageClient from '@/app/(app)/clientes/ClientesPageClient';
import ObrasPageClient from '@/app/(app)/obras/ObrasPageClient';
import PedidosPageClient from '@/app/(app)/pedidos/PedidosPageClient';
import ProductosPageClient from '@/app/(app)/productos/ProductosPageClient';
import UsuariosPageClient from '@/app/(app)/usuarios/UsuariosPageClient';
import ClienteForm from '@/components/ClienteForm';
import { FeedbackProvider } from '@/components/Feedback';
import PedidoForm from '@/components/PedidoForm';
import ProductoForm from '@/components/ProductoForm';
import UsuarioForm from '@/components/UsuarioForm';
import { clienteService } from '@/services/clienteService';
import { obraService } from '@/services/obraService';
import { pedidoService } from '@/services/pedidoService';
import { productoService } from '@/services/productoService';
import { usuarioService } from '@/services/usuarioService';

vi.mock('@/services/clienteService', () => ({ clienteService: { getAll: vi.fn(), delete: vi.fn() } }));
vi.mock('@/services/productoService', () => ({ productoService: { getAll: vi.fn(), delete: vi.fn(), getCategorias: vi.fn() } }));
vi.mock('@/services/obraService', () => ({ obraService: { getAll: vi.fn(), getByClienteId: vi.fn(), delete: vi.fn() } }));
vi.mock('@/services/pedidoService', () => ({ pedidoService: { search: vi.fn(), getAll: vi.fn(), delete: vi.fn() } }));
vi.mock('@/services/usuarioService', () => ({ usuarioService: { getAll: vi.fn(), delete: vi.fn() } }));
vi.mock('@/lib/auth/AuthContext', () => ({
  useAuth: () => ({ user: { email: 'a@a.com', nombre: 'Ana', rol: 'ADMIN' }, isAdmin: true }),
}));

const render = (ui: ReactElement) => renderBase(<FeedbackProvider>{ui}</FeedbackProvider>);

/**
 * Corre axe sobre la pantalla actual. No se miden el contraste de color (jsdom no calcula estilos) ni las
 * regiones de la página (<main> y navegación las pone el layout, que acá no se renderiza).
 */
async function violaciones () {
  const resultado = await axe.run(document.body, { rules: { 'color-contrast': { enabled: false }, region: { enabled: false } } });
  return resultado.violations.map((v) => `${v.id}: ${v.help} → ${v.nodes.map((n) => n.target.join(' ')).join(' | ')}`);
}

const cliente = { id: 1, nombre: 'Constructora Sur', correoElectronico: 'sur@mail.com', cuit: '30-1', maximoDescubierto: 1000 };
const obra = { id: 4, direccion: 'Calle 1', estado: 'HABILITADA', presupuesto: 100, cliente };
const producto = { id: 7, codigo: 'C1', nombre: 'Cemento', precio: 100, descuentoPromocional: 0, unidadMedida: 'BOLSA', stockActual: 10, stockMinimo: 1 };
const pedido = {
  id: 'p1',
  numeroPedido: 100001,
  usuario: 'ana',
  total: 1000,
  estado: 'EN_PREPARACION',
  fecha: '2026-01-01T10:00:00Z',
  cliente: { id: 1, nombre: 'Constructora Sur' },
  detalle: [],
};
const usuario = { id: 1, nombre: 'Ana', email: 'ana@mail.com', rol: 'ADMIN' };

beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(clienteService.getAll).mockResolvedValue([cliente] as never);
  vi.mocked(productoService.getAll).mockResolvedValue([producto] as never);
  vi.mocked(productoService.getCategorias).mockResolvedValue(['CEMENTOS'] as never);
  vi.mocked(obraService.getAll).mockResolvedValue([obra] as never);
  vi.mocked(obraService.getByClienteId).mockResolvedValue([obra] as never);
  vi.mocked(pedidoService.search).mockResolvedValue([pedido] as never);
  vi.mocked(usuarioService.getAll).mockResolvedValue([usuario] as never);
});

describe('accesibilidad (axe) de las pantallas principales', () => {
  it.each([
    ['clientes', <ClientesPageClient key='c' />, 'Constructora Sur'],
    ['obras', <ObrasPageClient key='o' />, 'Calle 1'],
    ['productos', <ProductosPageClient key='p' />, 'Cemento'],
    ['pedidos', <PedidosPageClient key='pe' />, '100001'],
    ['usuarios', <UsuariosPageClient key='u' />, 'ana@mail.com'],
  ])('la pantalla de %s no tiene violaciones', async (_nombre, pantalla, texto) => {
    render(pantalla);
    await screen.findByText(texto);

    expect(await violaciones()).toEqual([]);
  });

  it.each([
    ['cliente', <ClienteForm key='c' onSubmit={vi.fn()} onCancel={vi.fn()} />],
    ['producto', <ProductoForm key='p' onSubmit={vi.fn()} onCancel={vi.fn()} />],
    ['usuario', <UsuarioForm key='u' onSubmit={vi.fn()} onCancel={vi.fn()} />],
  ])('el formulario de %s no tiene violaciones', async (_nombre, formulario) => {
    render(formulario);
    await screen.findByRole('dialog');

    expect(await violaciones()).toEqual([]);
  });

  it('el formulario de pedido (con lineas) no tiene violaciones', async () => {
    render(<PedidoForm onSubmit={vi.fn()} onCancel={vi.fn()} />);
    await screen.findByRole('option', { name: 'Constructora Sur' });
    await userEvent.click(screen.getByRole('button', { name: '+ Agregar producto' }));
    await userEvent.click(screen.getByRole('button', { name: '+ Agregar producto' }));

    expect(await violaciones()).toEqual([]);
    expect(screen.getByLabelText('Producto 1')).toBeInTheDocument();
    expect(screen.getByLabelText('Producto 2')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Quitar producto 1' })).toBeInTheDocument();
  });

  it('la confirmacion de borrado tampoco tiene violaciones', async () => {
    render(<ClientesPageClient />);
    await screen.findByText('Constructora Sur');
    await userEvent.click(screen.getByRole('button', { name: /eliminar/i }));

    await screen.findByRole('dialog', { name: 'Eliminar cliente' });
    expect(await violaciones()).toEqual([]);
  });
});

describe('borrar sin dialogos nativos', () => {
  it('pide confirmacion en un dialogo propio y no llama a window.confirm', async () => {
    const confirmNativo = vi.spyOn(window, 'confirm');
    vi.mocked(clienteService.delete).mockResolvedValue(undefined as never);
    render(<ClientesPageClient />);
    await screen.findByText('Constructora Sur');

    await userEvent.click(screen.getByRole('button', { name: /eliminar/i }));
    const dialogo = await screen.findByRole('dialog', { name: 'Eliminar cliente' });
    expect(dialogo).toHaveTextContent('Constructora Sur');
    await userEvent.click(screen.getAllByRole('button', { name: 'Eliminar' }).at(-1)!);

    await waitFor(() => expect(clienteService.delete).toHaveBeenCalledWith(1));
    expect(confirmNativo).not.toHaveBeenCalled();
  });

  it('cancelar no borra', async () => {
    render(<ClientesPageClient />);
    await screen.findByText('Constructora Sur');

    await userEvent.click(screen.getByRole('button', { name: /eliminar/i }));
    await userEvent.click(await screen.findByRole('button', { name: 'Cancelar' }));

    expect(clienteService.delete).not.toHaveBeenCalled();
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('si el borrado falla lo avisa en la region en vivo (sin alert)', async () => {
    const alertNativo = vi.spyOn(window, 'alert').mockImplementation(() => {});
    vi.mocked(clienteService.delete).mockRejectedValue(new Error('x'));
    render(<ClientesPageClient />);
    await screen.findByText('Constructora Sur');

    await userEvent.click(screen.getByRole('button', { name: /eliminar/i }));
    await userEvent.click(screen.getAllByRole('button', { name: 'Eliminar' }).at(-1)!);

    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo eliminar el cliente.');
    expect(alertNativo).not.toHaveBeenCalled();
  });
});
