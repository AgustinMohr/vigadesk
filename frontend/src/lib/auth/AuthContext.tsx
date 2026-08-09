// ==============================================================
// CONTEXT DE AUTENTICACIÓN (el "estado global" del usuario)
// --------------------------------------------------------------
// PROBLEMA que resuelve: la sesión la necesitan MUCHOS componentes
// (Navbar, Sidebar, páginas, tablas...). Pasar el usuario por props por
// toda la app sería un desastre. React Context es como un "cable global":
// un Provider publica un valor y cualquier componente (en cualquier
// profundidad) lo lee con un hook.
//
// FLUJO DEL LOGIN (todos los usuarios van al backend):
//  1. login() llama a POST /api/usuarios/login → ms-clientes valida las
//     credenciales contra la base y devuelve un JWT firmado + los datos
//     del usuario (email, nombre, rol, y clienteId si es CLIENTE).
//  2. Guardamos dos cosas en localStorage:
//       - "auth_token": el JWT, que http.ts manda en cada request.
//       - "auth_user": el objeto User, para que la app lo use en el contexto
//         y para "sobrevivir" al refrescar la página.
//
// Componentes expuestos:
//  - <AuthProvider>: envuelve la app y "provee" el contexto.
//  - useAuth(): hook para LEER ese contexto desde cualquier componente.
// ==============================================================

'use client';

import { createContext, useContext, useState, useCallback, type ReactNode } from 'react';
import type { User, UserRole } from './types';
import { usuarioService } from '@/services/usuarioService';

// Forma del valor que el context expone. TypeScript nos obliga a que
// el Provider devuelva exactamente esto.
type AuthContextValue = {
  user: User | null; // null = no hay sesión
  login: (email: string, password: string) => Promise<boolean>;
  logout: () => void;
  isAdmin: boolean; // comodín para saber si el rol es ADMIN
};

// El context en sí. `createContext(null)` crea el "cable" sin valor
// inicial; el Provider le dará valor más abajo.
const AuthContext = createContext<AuthContextValue | null>(null);

// Lee el usuario guardado en localStorage.
// IMPORTANTE: si no hay `window` (SSR en el servidor), devuelve null,
// porque localStorage no existe en el servidor.
function getStoredUser (): User | null {
  if (typeof window === 'undefined') return null;
  const raw = localStorage.getItem('auth_user');
  if (!raw) return null;
  try {
    return JSON.parse(raw) as User; // el JSON se guardó con JSON.stringify
  } catch {
    return null; // JSON corrupto → no hay sesión
  }
}

// Provider: el componente que "publica" el estado a toda la app.
// Arranca con el usuario de localStorage (si hay) gracias a useState
// con inicializador lazy (getStoredUser se llama una vez al montar).
export function AuthProvider ({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(getStoredUser);

  // useCallback: mantiene la MISMA referencia de la función entre
  // renders (solo cambia si cambian sus dependencias). Sirve para que
  // los componentes hijos no se re-rendericen innecesariamente.
  const login = useCallback(async (email: string, password: string): Promise<boolean> => {
    // Todos los usuarios (admin, guest y clientes) se autentican contra
    // ms-clientes. Si el backend responde OK, devuelve el token firmado
    // + los datos del usuario. Guardamos token y User en localStorage.
    try {
      const apiUser = await usuarioService.login(email, password);
      const currentUser: User = {
        email: apiUser.email,
        nombre: apiUser.nombre,
        rol: apiUser.rol as UserRole, // "ADMIN" | "GUEST" | "CLIENTE"
        clienteId: apiUser.clienteId,
        clienteNombre: apiUser.clienteNombre,
      };
      localStorage.setItem('auth_token', apiUser.token);
      localStorage.setItem('auth_user', JSON.stringify(currentUser));
      setUser(currentUser);
      return true;
    } catch {
      return false; // credenciales inválidas o backend caído
    }
  }, []);

  // Cierra sesión: borra localStorage (token + usuario) y pone user en null.
  const logout = useCallback(() => {
    localStorage.removeItem('auth_token');
    localStorage.removeItem('auth_user');
    setUser(null);
  }, []);

  return (
    <AuthContext.Provider value={{ user, login, logout, isAdmin: user?.rol === 'ADMIN' }}>
      {children}
    </AuthContext.Provider>
  );
}

// Hook de lectura. Cualquier componente que lo llame se re-renderiza
// cuando cambia el contexto (ej: al loguearse). El `throw` protege:
// si alguien usa useAuth sin un Provider arriba, explota con un mensaje claro.
export function useAuth () {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth debe usarse dentro de un AuthProvider');
  return ctx;
}
