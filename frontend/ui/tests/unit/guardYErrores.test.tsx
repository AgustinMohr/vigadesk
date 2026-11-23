import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderToStaticMarkup } from 'react-dom/server';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ErrorDelArea from '@/app/(app)/error';
import Loading from '@/app/(app)/loading';
import ErrorGlobal from '@/app/global-error';
import NotFound from '@/app/not-found';
import AuthLayout from '@/app/(app)/AuthLayout';
import UsuariosPageClient from '@/app/(app)/usuarios/UsuariosPageClient';
import RoleGuard from '@/components/RoleGuard';
import Sidebar from '@/components/Sidebar';
import { puedeAbrir, RUTAS, rolesDeRuta } from '@/lib/auth/access';
import { usuarioService } from '@/services/usuarioService';

const sesion = vi.hoisted(() => ({ user: null as Record<string, unknown> | null, pathname: '/dashboard', replace: vi.fn() }));

vi.mock('next/navigation', () => ({
  usePathname: () => sesion.pathname,
  useRouter: () => ({ replace: sesion.replace }),
}));
vi.mock('@/lib/auth/AuthContext', () => ({
  AuthProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  useAuth: () => ({ user: sesion.user, isAdmin: sesion.user?.rol === 'ADMIN' }),
}));
vi.mock('@/services/usuarioService', () => ({ usuarioService: { getAll: vi.fn() } }));
vi.mock('@/components/Navbar', () => ({ default: () => <nav>barra</nav> }));

const ADMIN = { email: 'a@a.com', nombre: 'Ana', rol: 'ADMIN' };
const CLIENTE = { email: 'c@c.com', nombre: 'Cli', rol: 'CLIENTE', clienteId: 1 };
const GUEST = { email: 'g@g.com', nombre: 'Gus', rol: 'GUEST' };

beforeEach(() => {
  vi.resetAllMocks();
  sesion.user = null;
  sesion.pathname = '/dashboard';
});

describe('acceso por ruta (access.ts)', () => {
  it.each([
    ['/usuarios', 'ADMIN', true], ['/usuarios', 'CLIENTE', false], ['/usuarios', 'GUEST', false],
    ['/clientes', 'CLIENTE', false], ['/obras', 'GUEST', false], ['/importar', 'CLIENTE', false],
    ['/pedidos', 'CLIENTE', true], ['/pedidos', 'GUEST', false],
    ['/productos', 'GUEST', true], ['/dashboard', 'CLIENTE', true],
    ['/usuarios/5', 'CLIENTE', false], // subrutas heredan la restriccion
  ] as const)('%s con rol %s → %s', (ruta, rol, esperado) => {
    expect(puedeAbrir(rol, ruta)).toBe(esperado);
  });

  it('una ruta que no esta en la tabla no se restringe (la decide el 404)', () => {
    expect(rolesDeRuta('/no-existe')).toBeNull();
    expect(puedeAbrir('GUEST', '/no-existe')).toBe(true);
  });

  it('un prefijo parecido no cuenta como la misma ruta', () => {
    expect(rolesDeRuta('/usuariosx')).toBeNull();
  });

  it('toda ruta del menu es una pantalla con restriccion declarada', () => {
    for (const ruta of RUTAS) expect(ruta.roles.length).toBeGreaterThan(0);
  });
});

describe('RoleGuard', () => {
  it('con el rol permitido muestra la pantalla', () => {
    render(<RoleGuard rol='ADMIN' roles={['ADMIN']}><p>contenido</p></RoleGuard>);

    expect(screen.getByText('contenido')).toBeInTheDocument();
  });

  it('sin permiso muestra "Sin acceso" y no monta la pantalla', () => {
    const montado = vi.fn();
    function Pantalla () { montado(); return <p>contenido</p>; }

    render(<RoleGuard rol='CLIENTE' roles={['ADMIN']}><Pantalla /></RoleGuard>);

    expect(screen.getByRole('alert')).toHaveTextContent('Sin acceso');
    expect(screen.getByRole('link', { name: 'Volver al inicio' })).toHaveAttribute('href', '/dashboard');
    expect(montado).not.toHaveBeenCalled();
  });

  it('sin sesion todavia muestra un indicador de carga, no una pantalla en blanco', () => {
    render(<RoleGuard rol={undefined} roles={['ADMIN']}><p>contenido</p></RoleGuard>);

    expect(screen.getByRole('status')).toHaveTextContent('Cargando');
    expect(screen.queryByText('contenido')).toBeNull();
  });

  it('una ruta sin restriccion deja pasar a cualquier rol', () => {
    render(<RoleGuard rol='GUEST' roles={null}><p>contenido</p></RoleGuard>);

    expect(screen.getByText('contenido')).toBeInTheDocument();
  });
});

describe('AuthLayout (guard de rutas)', () => {
  it('un CLIENTE que abre /usuarios ve "Sin acceso" y no hay llamadas al backend', () => {
    sesion.user = CLIENTE;
    sesion.pathname = '/usuarios';

    render(<AuthLayout><UsuariosPageClient /></AuthLayout>);

    expect(screen.getByRole('alert')).toHaveTextContent('Sin acceso');
    expect(usuarioService.getAll).not.toHaveBeenCalled();
  });

  it('un ADMIN que abre /usuarios carga la pantalla', async () => {
    sesion.user = ADMIN;
    sesion.pathname = '/usuarios';
    vi.mocked(usuarioService.getAll).mockResolvedValue([]);

    render(<AuthLayout><UsuariosPageClient /></AuthLayout>);

    expect(await screen.findByRole('heading', { name: /usuarios/i })).toBeInTheDocument();
    expect(usuarioService.getAll).toHaveBeenCalled();
    expect(screen.queryByText('Sin acceso')).toBeNull();
  });

  it('sin sesion redirige al login y muestra el indicador de carga', () => {
    sesion.user = null;

    render(<AuthLayout><p>privado</p></AuthLayout>);

    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(screen.queryByText('privado')).toBeNull();
    expect(sesion.replace).toHaveBeenCalledWith('/login');
  });
});

describe('menu lateral', () => {
  it.each([
    ['ADMIN', ADMIN, ['Dashboard', 'Clientes', 'Obras', 'Usuarios', 'Productos', 'Pedidos', 'Importar CSV']],
    ['CLIENTE', CLIENTE, ['Dashboard', 'Productos', 'Pedidos']],
    ['GUEST', GUEST, ['Dashboard', 'Productos']],
  ])('%s ve solo lo que puede abrir', (_rol, usuario, esperados) => {
    sesion.user = usuario;

    render(<Sidebar />);

    expect(screen.getAllByRole('link').map((l) => l.textContent)).toEqual(esperados);
  });
});

describe('paginas de error', () => {
  it('error de la pantalla: mensaje y boton Reintentar que llama a reset', async () => {
    const reset = vi.fn();
    const consola = vi.spyOn(console, 'error').mockImplementation(() => {});
    const error = new Error('boom');

    render(<ErrorDelArea error={error} reset={reset} />);

    expect(screen.getByRole('alert')).toHaveTextContent('Algo salió mal');
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(reset).toHaveBeenCalledTimes(1);
    expect(consola).toHaveBeenCalledWith(error);
    consola.mockRestore();
  });

  it('error global: trae su propio html y body', () => {
    const html = renderToStaticMarkup(<ErrorGlobal error={new Error('x')} reset={() => {}} />);

    expect(html).toContain('<html lang="es">');
    expect(html).toContain('Algo salió mal');
    expect(html).toContain('Reintentar');
  });

  it('404: mensaje y enlace al inicio', () => {
    render(<NotFound />);

    expect(screen.getByRole('heading', { name: 'Página no encontrada' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ir al inicio' })).toHaveAttribute('href', '/dashboard');
  });

  it('loading de la ruta: indicador accesible', () => {
    render(<Loading />);

    expect(screen.getByRole('status')).toBeInTheDocument();
  });
});
