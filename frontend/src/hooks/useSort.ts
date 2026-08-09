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
// ==============================================================

'use client';

import { useMemo, useState } from 'react';

export type SortDir = 'asc' | 'desc';

export function useSort<T> (
  data: T[], // la lista a ordenar
  defaultKey?: string, // columna inicial (opcional)
  getValue?: (item: T, key: string) => string | number // cómo sacar el valor a comparar
) {
  // Estado: columna ordenada y dirección (asc/desc).
  const [sortKey, setSortKey] = useState<string | undefined>(defaultKey);
  const [sortDir, setSortDir] = useState<SortDir>('asc');

  // Alterna: si clikeamos la misma columna, invierte el orden;
  // si es otra columna, ordena asc desde cero.
  const toggleSort = (key: string) => {
    if (key === sortKey) {
      setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortKey(key);
      setSortDir('asc');
    }
  };

  // useMemo: la lista ordenada se recalcula solo cuando cambian
  // [data, sortKey, sortDir, getValue]. La copiamos con [...data]
  // para no modificar el array original (inmutabilidad).
  const sortedData = useMemo(() => {
    if (!sortKey) return data;

    return [...data].sort((a, b) => {
      // Si el hook recibió getValue, lo usa; si no, accede directo a la
      // propiedad con la sintaxis `(a as any)[sortKey]` (acceso dinámico).

      const aVal = getValue ? getValue(a, sortKey) : (a as any)[sortKey];

      const bVal = getValue ? getValue(b, sortKey) : (b as any)[sortKey];

      // Valores nulos/undefined van siempre al final de la lista.
      if (aVal == null && bVal == null) return 0;
      if (aVal == null) return 1;
      if (bVal == null) return -1;

      // Strings → comparación por orden alfabético con locale "es".
      if (typeof aVal === 'string' && typeof bVal === 'string') {
        return sortDir === 'asc'
          ? aVal.localeCompare(bVal, 'es')
          : bVal.localeCompare(aVal, 'es');
      }
      // Números → resta (negativa = a antes, positiva = b antes).
      return sortDir === 'asc'
        ? (aVal as number) - (bVal as number)
        : (bVal as number) - (aVal as number);
    });
  }, [data, sortKey, sortDir, getValue]);

  // Devuelve el indicador visual de la columna ordenada ("▲"/"▼").
  const indicator = (key: string): string => {
    if (key !== sortKey) return '';
    return sortDir === 'asc' ? ' ▲' : ' ▼';
  };

  // Lo que "expone" el hook a quien lo use.
  return { sortedData, sortKey, sortDir, toggleSort, indicator };
}
