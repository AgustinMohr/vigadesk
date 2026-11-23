import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import LoginPage from '@/app/login/page';
import Navbar from '@/components/Navbar';
import Sidebar from '@/components/Sidebar';
import SortableTh from '@/components/SortableTh';
import ClienteTable from '@/components/ClienteTable';
import { useSort } from '@/hooks/useSort';

const sesion = vi.hoisted(() => ({
  user: null as Record<string, unknown> | null,
  pathname: '/dashboard',
  login: vi.fn(),
  logout: vi.fn(),
  replace: vi.fn(),
}));

vi.mock('next/navigation', () => ({
  usePathname: () => sesion.pathname,
  useRouter: () => ({ replace: sesion.replace }),
}));
vi.mock('@/lib/auth/AuthContext', () => ({
  AuthProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  useAuth: () => ({ user: sesion.user, isAdmin: sesion.user?.rol === 'ADMIN', login: sesion.login, logout: sesion.logout }),
}));

const ADMIN = { email: 'a@a.com', nombre: 'Ana', rol: 'ADMIN' };

beforeEach(() => {
  vi.resetAllMocks();
  sesion.user = ADMIN;
  sesion.pathname = '/dashboard';
});

describe('menu lateral', () => {
  it('marca la ruta activa con aria-current="page" (tambien en subrutas) y solo esa', () => {
    sesion.pathname = '/pedidos/abc';

    render(<Sidebar />);

    expect(screen.getByRole('link', { name: 'Pedidos' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: 'Productos' })).not.toHaveAttribute('aria-current');
    expect(screen.getAllByRole('link').filter((l) => l.getAttribute('aria-current') === 'page')).toHaveLength(1);
  });

  it('es una navegacion con nombre', () => {
    render(<Sidebar />);

    expect(screen.getByRole('navigation', { name: 'Principal' })).toBeInTheDocument();
  });

  it('en pantallas chicas: se cierra con Esc, con el boton Cerrar menu y al elegir una opcion', async () => {
    const onCerrar = vi.fn();
    render(<Sidebar abierto onCerrar={onCerrar} />);

    await userEvent.keyboard('{Escape}');
    expect(onCerrar).toHaveBeenCalledTimes(1);
    await userEvent.click(screen.getByRole('button', { name: 'Cerrar menú' }));
    expect(onCerrar).toHaveBeenCalledTimes(2);
    await userEvent.click(screen.getByRole('link', { name: 'Clientes' }));
    expect(onCerrar).toHaveBeenCalledTimes(3);
  });

  it('cerrado no escucha Esc', async () => {
    const onCerrar = vi.fn();
    render(<Sidebar abierto={false} onCerrar={onCerrar} />);

    await userEvent.keyboard('{Escape}');

    expect(onCerrar).not.toHaveBeenCalled();
  });
});

describe('barra superior', () => {
  it('muestra la marca VigaDesk y el boton de menu enlazado al panel lateral', async () => {
    const onMenu = vi.fn();
    render(<Navbar menuAbierto={false} onMenu={onMenu} />);

    expect(screen.getByRole('link', { name: 'VigaDesk' })).toHaveAttribute('href', '/dashboard');
    const boton = screen.getByRole('button', { name: 'Abrir menú' });
    expect(boton).toHaveAttribute('aria-expanded', 'false');
    expect(boton).toHaveAttribute('aria-controls', 'menu-lateral');
    await userEvent.click(boton);
    expect(onMenu).toHaveBeenCalled();
  });

  it('Salir es un boton y cierra la sesion', async () => {
    render(<Navbar />);

    await userEvent.click(screen.getByRole('button', { name: 'Salir' }));

    expect(sesion.logout).toHaveBeenCalledTimes(1);
  });
});

describe('encabezados ordenables', () => {
  it('SortableTh declara aria-sort y se opera con teclado', async () => {
    const onSort = vi.fn();
    render(
      <table>
        <thead>
          <tr>
            <SortableTh clave='nombre' sortKey='nombre' sortDir='desc' onSort={onSort}>Nombre</SortableTh>
            <SortableTh clave='cuit' sortKey='nombre' sortDir='desc' onSort={onSort}>CUIT</SortableTh>
          </tr>
        </thead>
      </table>
    );

    expect(screen.getByRole('columnheader', { name: /Nombre/ })).toHaveAttribute('aria-sort', 'descending');
    expect(screen.getByRole('columnheader', { name: /CUIT/ })).toHaveAttribute('aria-sort', 'none');

    await userEvent.tab();
    expect(screen.getByRole('button', { name: /Nombre/ })).toHaveFocus();
    await userEvent.keyboard('{Enter}');
    await userEvent.tab();
    await userEvent.keyboard(' ');
    expect(onSort.mock.calls).toEqual([['nombre'], ['cuit']]);
  });

  it('en una tabla real, ordenar con Enter cambia aria-sort y el orden de las filas', async () => {
    function Tabla () {
      const [datos] = useState([{ id: 2, nombre: 'Beta' }, { id: 1, nombre: 'Alfa' }]);
      const { sortedData, sortKey, sortDir, toggleSort } = useSort(datos, undefined);
      return (
        <table>
          <thead><tr><SortableTh clave='nombre' sortKey={sortKey} sortDir={sortDir} onSort={toggleSort}>Nombre</SortableTh></tr></thead>
          <tbody>{sortedData.map((d) => <tr key={d.id}><td>{d.nombre}</td></tr>)}</tbody>
        </table>
      );
    }
    render(<Tabla />);
    const celdas = () => screen.getAllByRole('cell').map((c) => c.textContent);
    expect(celdas()).toEqual(['Beta', 'Alfa']);

    await userEvent.tab();
    await userEvent.keyboard('{Enter}');
    expect(celdas()).toEqual(['Alfa', 'Beta']);
    expect(screen.getByRole('columnheader')).toHaveAttribute('aria-sort', 'ascending');
    await userEvent.keyboard('{Enter}');
    expect(celdas()).toEqual(['Beta', 'Alfa']);
    expect(screen.getByRole('columnheader')).toHaveAttribute('aria-sort', 'descending');
  });

  it('las tablas del sistema usan encabezados ordenables operables con teclado', () => {
    render(<ClienteTable clientes={[]} onEdit={vi.fn()} onDelete={vi.fn()} onView={vi.fn()} onAssignObra={vi.fn()} />);

    const ordenables = screen.getAllByRole('columnheader').filter((h) => h.hasAttribute('aria-sort'));
    expect(ordenables.length).toBeGreaterThanOrEqual(3);
    for (const h of ordenables) expect(h.querySelector('button')).not.toBeNull();
  });
});

describe('login', () => {
  it('los campos declaran autocomplete para gestores de contrasenas', () => {
    render(<LoginPage />);

    expect(screen.getByLabelText('Email')).toHaveAttribute('autocomplete', 'username');
    expect(screen.getByLabelText('Contraseña')).toHaveAttribute('autocomplete', 'current-password');
  });

  it('mientras envia, el boton se deshabilita y dice "Ingresando…"; si falla vuelve a habilitarse y avisa', async () => {
    let resolver!: (ok: boolean) => void;
    sesion.login.mockReturnValue(new Promise<boolean>((resolve) => { resolver = resolve; }));
    render(<LoginPage />);
    await userEvent.type(screen.getByLabelText('Email'), 'a@a.com');
    await userEvent.type(screen.getByLabelText('Contraseña'), 'clave');

    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    const enviando = screen.getByRole('button', { name: 'Ingresando…' });
    expect(enviando).toBeDisabled();
    resolver(false);
    expect(await screen.findByRole('alert')).toHaveTextContent('Email o contraseña incorrectos.');
    await waitFor(() => expect(screen.getByRole('button', { name: 'Entrar' })).toBeEnabled());
  });

  it('con credenciales correctas navega al dashboard', async () => {
    sesion.login.mockResolvedValue(true);
    render(<LoginPage />);
    await userEvent.type(screen.getByLabelText('Email'), 'a@a.com');
    await userEvent.type(screen.getByLabelText('Contraseña'), 'clave');

    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    await waitFor(() => expect(sesion.replace).toHaveBeenCalledWith('/dashboard'));
  });
});
