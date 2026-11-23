import { act, renderHook } from '@testing-library/react';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider, useAuth } from '@/lib/auth/AuthContext';
import { toUserRole } from '@/lib/auth/types';
import { usuarioService } from '@/services/usuarioService';

vi.mock('@/services/usuarioService', () => ({ usuarioService: { login: vi.fn() } }));

const login = vi.mocked(usuarioService.login);

const wrapper = ({ children }: { children: ReactNode }) => <AuthProvider>{children}</AuthProvider>;

const respuestaLogin = {
  token: 'jwt-1',
  email: 'ana@mail.com',
  nombre: 'Ana',
  rol: 'CLIENTE',
  clienteId: 5,
  clienteNombre: 'Constructora Sur',
};

beforeEach(() => {
  login.mockReset();
});

describe('AuthContext', () => {
  it('arranca sin sesion', () => {
    const { result } = renderHook(() => useAuth(), { wrapper });

    expect(result.current.user).toBeNull();
    expect(result.current.isAdmin).toBe(false);
  });

  it('login correcto guarda token y usuario y actualiza el estado', async () => {
    login.mockResolvedValue(respuestaLogin as never);
    const { result } = renderHook(() => useAuth(), { wrapper });

    let ok = false;
    await act(async () => { ok = await result.current.login('ana@mail.com', 'x'); });

    expect(ok).toBe(true);
    expect(result.current.user).toEqual({
      email: 'ana@mail.com', nombre: 'Ana', rol: 'CLIENTE', clienteId: 5, clienteNombre: 'Constructora Sur',
    });
    expect(localStorage.getItem('auth_token')).toBe('jwt-1');
    expect(JSON.parse(localStorage.getItem('auth_user')!).email).toBe('ana@mail.com');
    expect(localStorage.getItem('auth_user')).not.toContain('jwt-1'); // el token no va dentro del usuario
  });

  it('login fallido devuelve false y no deja sesion', async () => {
    login.mockRejectedValue(new Error('401'));
    const { result } = renderHook(() => useAuth(), { wrapper });

    let ok = true;
    await act(async () => { ok = await result.current.login('ana@mail.com', 'mal'); });

    expect(ok).toBe(false);
    expect(result.current.user).toBeNull();
    expect(localStorage.getItem('auth_token')).toBeNull();
  });

  it('un ADMIN activa isAdmin', async () => {
    login.mockResolvedValue({ ...respuestaLogin, rol: 'ADMIN', clienteId: undefined } as never);
    const { result } = renderHook(() => useAuth(), { wrapper });

    await act(async () => { await result.current.login('a@a.com', 'x'); });

    expect(result.current.isAdmin).toBe(true);
  });

  it('logout limpia el estado y el almacenamiento', async () => {
    login.mockResolvedValue(respuestaLogin as never);
    const { result } = renderHook(() => useAuth(), { wrapper });
    await act(async () => { await result.current.login('ana@mail.com', 'x'); });

    act(() => result.current.logout());

    expect(result.current.user).toBeNull();
    expect(localStorage.getItem('auth_token')).toBeNull();
    expect(localStorage.getItem('auth_user')).toBeNull();
  });

  it('restaura la sesion guardada al montar', () => {
    localStorage.setItem('auth_user', JSON.stringify({ email: 'b@b.com', nombre: 'B', rol: 'GUEST' }));

    const { result } = renderHook(() => useAuth(), { wrapper });

    expect(result.current.user?.email).toBe('b@b.com');
  });

  it('un JSON corrupto guardado se trata como sin sesion', () => {
    localStorage.setItem('auth_user', '{no es json');

    const { result } = renderHook(() => useAuth(), { wrapper });

    expect(result.current.user).toBeNull();
  });

  it('useAuth fuera del proveedor lanza un error claro', () => {
    const silencio = vi.spyOn(console, 'error').mockImplementation(() => {});

    expect(() => renderHook(() => useAuth())).toThrow('useAuth debe usarse dentro de un AuthProvider');

    silencio.mockRestore();
  });
});

describe('toUserRole', () => {
  it('acepta los roles conocidos y degrada el resto a GUEST', () => {
    expect(toUserRole('ADMIN')).toBe('ADMIN');
    expect(toUserRole('CLIENTE')).toBe('CLIENTE');
    expect(toUserRole('SUPERUSUARIO')).toBe('GUEST');
    expect(toUserRole('')).toBe('GUEST');
  });
});
