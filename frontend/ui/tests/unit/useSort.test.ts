import { act, renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { useSort } from '@/hooks/useSort';

type Fila = { nombre: string | null; monto: number | null };

const datos: Fila[] = [
  { nombre: 'banana', monto: 30 },
  { nombre: 'Árbol', monto: 10 },
  { nombre: null, monto: null },
  { nombre: 'cedro', monto: 20 },
];

describe('useSort', () => {
  it('sin columna inicial devuelve los datos tal cual', () => {
    const { result } = renderHook(() => useSort(datos));

    expect(result.current.sortedData).toBe(datos);
    expect(result.current.indicator('nombre')).toBe('');
  });

  it('ordena texto ascendente con orden de espanol (la A con tilde va primero)', () => {
    const { result } = renderHook(() => useSort(datos, 'nombre'));

    expect(result.current.sortedData.map(f => f.nombre)).toEqual(['Árbol', 'banana', 'cedro', null]);
    expect(result.current.indicator('nombre')).toBe(' ▲');
  });

  it('toggleSort sobre la misma columna alterna el sentido y los nulos quedan al final', () => {
    const { result } = renderHook(() => useSort(datos, 'monto'));

    act(() => result.current.toggleSort('monto'));

    expect(result.current.sortDir).toBe('desc');
    expect(result.current.sortedData.map(f => f.monto)).toEqual([30, 20, 10, null]);
    expect(result.current.indicator('monto')).toBe(' ▼');
  });

  it('toggleSort sobre otra columna arranca en ascendente', () => {
    const { result } = renderHook(() => useSort(datos, 'monto'));
    act(() => result.current.toggleSort('monto')); // desc

    act(() => result.current.toggleSort('nombre'));

    expect(result.current.sortKey).toBe('nombre');
    expect(result.current.sortDir).toBe('asc');
    expect(result.current.indicator('monto')).toBe('');
  });

  it('no modifica el arreglo original', () => {
    const copia = [...datos];
    const { result } = renderHook(() => useSort(datos, 'monto'));

    expect(result.current.sortedData).toHaveLength(datos.length);

    expect(datos).toEqual(copia);
  });

  it('usa getValue para columnas calculadas', () => {
    const { result } = renderHook(() =>
      useSort(datos, 'largo', (fila, clave) => (clave === 'largo' ? (fila.nombre ?? '').length : 0)));

    expect(result.current.sortedData.map(f => f.nombre)).toEqual([null, 'Árbol', 'cedro', 'banana']);
  });
});
