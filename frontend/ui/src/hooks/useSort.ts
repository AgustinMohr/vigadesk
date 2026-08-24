// ==============================================================
// CUSTOM HOOK: useSort — ordenar cualquier lista
// --------------------------------------------------------------
// Un "custom hook" es una función que reutiliza hooks de React
// (useState, useMemo, etc.) y empieza con "use". Permite extraer lógica
// repetida para usarla en varios componentes.
//
// ¿Qué hace este? Recibe una lista y la devuelve ordenada, junto con
// funciones para cambiar la columna/dirección de orden. Lo usan las
// tablas (ObraTable, ClienteTable, etc.).
//
// GENÉRICO <T>: el hook funciona con CUALQUIER tipo de dato. <T> es un
// "placeholder": quien use el hook decide qué tipo es (ej: T = Obra).
//
// CONCEPTOS CLAVE: hooks personalizados, Genéricos, useMemo para
// memoización, inmutabilidad ([...data]) y acceso dinámico a propiedades.
// ==============================================================

'use client';

import { useMemo, useState } from 'react';

type SortValue = string | number | null | undefined;

/**
 * Dirección de ordenamiento posible de una columna.
 * - 'asc' → ascendente (A→Z / menor→mayor)
 * - 'desc' → descendente (Z→A / mayor→menor)
 */
export type SortDir = 'asc' | 'desc';

/**
 * Custom hook que encapsula la lógica de ordenamiento de una lista.
 *
 * CÓMO FUNCIONA POR DENTRO:
 *  - `useState` guarda la columna activa (`sortKey`) y su dirección (`sortDir`).
 *  - `toggleSort(key)` es el controlador: si se toca la misma columna,
 *    invierte la dirección; si es otra, la activa en 'asc'.
 *  - `useMemo` recalcula la lista ordenada SOLO cuando cambian sus
 *    dependencias (paso inútil de render se evita).
 *
 * VALORES NULOS: las filas con valor null/undefined SIEMPRE van al final,
 * sin importar la dirección (regla de negocio de las tablas).
 *
 * @template T - Tipo de los items de la lista (ej: Obra, Cliente...).
 * @param {T[]} data - La lista original a ordenar (NO se modifica).
 * @param {string} [defaultKey] - Columna inicial activa (opcional).
 * @param {(item: T, key: string) => string | number} [getValue] - Función
 *   opcional para extraer el valor a comparar de cada item. Si no se pasa,
 *   se accede directo a la propiedad con `item[sortKey]` (acceso dinámico).
 * @returns {{ sortedData: T[], sortKey, sortDir, toggleSort, indicator }}
 *   - `sortedData`: la lista ordenada (copia, no muta el original).
 *   - `sortKey`/`sortDir`: estado actual para pintar la columna activa.
 *   - `toggleSort(key)`: cambia/alterna el orden desde la tabla.
 *   - `indicator(key)`: devuelve " ▲"/" ▼" si esa columna está activa.
 */
export function useSort<T> (
  data: T[], // la lista a ordenar
  defaultKey?: string, // columna inicial (opcional)
  getValue?: (item: T, key: string) => string | number // cómo sacar el valor a comparar
) {
  // Estado: columna ordenada y dirección (asc/desc).
  // useState<string | undefined> porque al inicio puede no haber columna.
  const [sortKey, setSortKey] = useState<string | undefined>(defaultKey);
  const [sortDir, setSortDir] = useState<SortDir>('asc');

  /**
   * Alterna el ordenamiento: si se cliquea la misma columna, invierte la
   * dirección; si es otra columna, ordena ascedente desde cero.
   *
   * @param {string} key - Clave de la columna que el usuario tocó.
   */
  const toggleSort = (key: string) => {
    if (key === sortKey) {
      // Misma columna → el functional update usa el valor PREVIO de sortDir
      // (evita bugs de closures si hubiera renders encolados).
      setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortKey(key);
      setSortDir('asc');
    }
  };

  // useMemo: la lista ordenada se recalcula solo cuando cambian
  // [data, sortKey, sortDir, getValue]. Sin useMemo, se reordenaría en
  // CADA render → trabajo inútil para el JS main thread.
  const sortedData = useMemo(() => {
    // Sin columna activa → devolvemos la lista tal cual (orden original).
    if (!sortKey) return data;

    // Extrae el valor a comparar usando getValue si se pasó, o acceso
    // dinámico a la propiedad con `[sortKey]`. El cast a Record evita `any`.
    const valueOf = (item: T, key: string): SortValue =>
      getValue ? getValue(item, key) : (item as Record<string, SortValue>)[key];

    // `[...data]` — SPREAD: crea una copia superficial del array.
    // .sort() MUTA el array que lo invoca, así que si pasáramos `data`
    // directo modificaríamos el estado original del componente (anti-patrón).
    return [...data].sort((a, b) => {
      const aVal = valueOf(a, sortKey);
      const bVal = valueOf(b, sortKey);

      // Valores nulos/undefined van siempre al final de la lista.
      // `== null` (igualdad débil) cubre null Y undefined en una línea.
      if (aVal == null && bVal == null) return 0;  // ambos nulos → sin cambio
      if (aVal == null) return 1;                  // a nulo → a va después
      if (bVal == null) return -1;                 // b nulo → b va después

      // Strings → comparación alfabética con localeConfigured "es" para
      // que acentos y ñ se ordenen correctamente en español. En 'desc' se
      // invierten los operandos (b.localeCompare(a) invierte el resultado).
      if (typeof aVal === 'string' && typeof bVal === 'string') {
        return sortDir === 'asc'
          ? aVal.localeCompare(bVal, 'es')
          : bVal.localeCompare(aVal, 'es');
      }

      // Números → resta. Si (a - b) < 0, a va antes (ascendente); en 'desc'
      // se invierte la resta para obtener el orden contrario.
      return sortDir === 'asc'
        ? Number(aVal) - Number(bVal)
        : Number(bVal) - Number(aVal);
    });
  }, [data, sortKey, sortDir, getValue]);

  /**
   * Devuelve el indicador visual de la columna ordenada ("▲"/"▼").
   *
   * @param {string} key - Clave de la columna a consultar.
   * @returns {string} " ▲" si está activa ascendente, " ▼" si descendente,
   *   o "" (vacío) si la columna no es la ordenada.
   */
  const indicator = (key: string): string => {
    if (key !== sortKey) return '';
    return sortDir === 'asc' ? ' ▲' : ' ▼';
  };

  // Lo que "expone" el hook a quien lo use.
  return { sortedData, sortKey, sortDir, toggleSort, indicator };
}
