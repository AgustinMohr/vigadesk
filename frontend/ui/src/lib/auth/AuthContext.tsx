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
//
// CONCEPTO CLAVE — localStorage vs SSR: el navegador persiste el estado en
// localStorage (disco). El servidor de Next.js NO tiene ese almacén, por eso
// TODA lectura directa del storage requiere el guardado de `typeof window`.
// ==============================================================

'use client';

import { createContext, useContext, useState, useCallback, type ReactNode } from 'react';
import { type User, toUserRole } from './types';
import { usuarioService } from '@/services/usuarioService';

/**
 * Forma del valor que el context expone. TypeScript nos obliga a que el
 * Provider devuelva exactamente esta forma (contrato del "cable").
 *
 * @typedef {Object} AuthContextValue
 * @property {User|null} user - Usuario logueado, o null si no hay sesión.
 * @property {(email: string, password: string) => Promise<boolean>} login -
 *   Autentica contra el backend. Resuelve `true` si el login fue exitoso.
 * @property {() => void} logout - Cierra la sesión y limpia storage.
 * @property {boolean} isAdmin - Comodín booleano: true si el rol es ADMIN.
 */
type AuthContextValue = {
  user: User | null; // null = no hay sesión
  login: (email: string, password: string) => Promise<boolean>;
  logout: () => void;
  isAdmin: boolean; // comodín para saber si el rol es ADMIN
};

// El context en sí. `createContext(null)` crea el "cable" sin valor
// inicial; el Provider le dará valor más abajo. El tipo es
// `AuthContextValue | null` para que useAuth pueda detectar la ausencia
// de Provider y lanzar un error con mensaje claro.
const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * Lee el usuario guardado en localStorage y lo parsea de vuelta a objeto.
 *
 * IMPORTANTE: si no hay `window` (SSR en el servidor), devuelve null,
 * porque localStorage no existe en el servidor. El try/catch protege
 * contra un JSON corrupto (el storage es un archivo de texto editable).
 *
 * @returns {User | null} El usuario deserializado, o null si no hay sesión
 *   o si el JSON guardado está dañado.
 */
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

/**
 * Provider: el componente que "publica" el estado de autenticación a toda
 * la app. Se monta una sola vez en el layout raíz (app/layout.tsx).
 *
 * Arranca con el usuario de localStorage (si hay) gracias a useState con
 * inicializador lazy: `getStoredUser` se pasa COMO FUNCIÓN, no como valor,
 * por lo que React lo ejecuta una única vez al montar (no en cada render).
 *
 * @param {{children: ReactNode}} props - ReactNode = cualquier JSX válido
 *   (hijos anidados que consumirán el contexto).
 */
export function AuthProvider ({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(getStoredUser);

  /**
   * Autentica al usuario contra el backend (ms-clientes) y arma la sesión.
   *
   * useCallback: mantiene la MISMA referencia de la función entre renders
   * (solo cambia si cambian sus dependencias — acá [] => nunca). Sirve
   * para que los componentes hijos memoizados no se re-rendericen.
   *
   * @param {string} email - Email del usuario (login).
   * @param {string} password - Contraseña en texto plano (solo viaja en el body).
   * @returns {Promise<boolean>} true si el login fue exitoso (sesión armada);
   *   false si las credenciales fallaron o el backend está caído (excepción).
   */
  const login = useCallback(async (email: string, password: string): Promise<boolean> => {
    // Todos los usuarios (admin, guest y clientes) se autentican contra
    // ms-clientes. Si el backend responde OK, devuelve el token firmado
    // + los datos del usuario. Guardamos token y User en localStorage.
    try {
      const apiUser = await usuarioService.login(email, password);

      // Mapeo manual: la respuesta plana del backend se adapta al tipo
      // `User` tipado del frontend. `toUserRole` valida el rol en runtime
      // y degrada a 'GUEST' si el backend devolviera algo inesperado.
      const currentUser: User = {
        email: apiUser.email,
        nombre: apiUser.nombre,
        rol: toUserRole(apiUser.rol),
        clienteId: apiUser.clienteId,
        clienteNombre: apiUser.clienteNombre,
      };

      // Persistencia de la sesión en el cliente. El token queda disponible
      // para que http.ts lo envíe como "Authorization: Bearer <token>" en
      // CADA request posterior. El User se guarda serializado (string).
      localStorage.setItem('auth_token', apiUser.token);
      localStorage.setItem('auth_user', JSON.stringify(currentUser));

      // Actualiza el estado React → se re-renderizan los consumidores
      // (Navbar, páginas protegidas, etc.).
      setUser(currentUser);
      return true;
    } catch {
      return false; // credenciales inválidas o backend caído
    }
  }, []);

  /**
   * Cierra la sesión: borra token y usuario de localStorage y pone user
   * en null. El componente que lo invoque decide adónde redirigir
   * (el Navbar, por ejemplo, lo manda a /login).
   *
   * @returns {void}
   */
  const logout = useCallback(() => {
    localStorage.removeItem('auth_token');
    localStorage.removeItem('auth_user');
    setUser(null);
  }, []);

  return (
    // `isAdmin: user?.rol === 'ADMIN'` — el optional chaining (`?.`) hace
    // que si user es null, la expresión valga undefined (o, aquí, false),
    // SIN tirar error de "Cannot read properties of null".
    <AuthContext.Provider value={{ user, login, logout, isAdmin: user?.rol === 'ADMIN' }}>
      {children}
    </AuthContext.Provider>
  );
}

/**
 * Hook de lectura del contexto de autenticación.
 *
 * Cualquier componente que lo llame se re-renderiza cuando cambia el
 * contexto (ej: al loguearse). El `throw` protege: si alguien usa useAuth
 * sin un Provider arriba en el árbol, el error con mensaje claro se dispara
 * al instante en desarrollo (mucho mejor que un undefined silencioso).
 *
 * @returns {AuthContextValue} El objeto con user, login, logout e isAdmin.
 */
export function useAuth () {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth debe usarse dentro de un AuthProvider');
  return ctx;
}
