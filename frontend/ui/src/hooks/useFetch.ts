'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { getErrorMessage, isApiError } from '@/lib/http/api-error';

type ReloadOptions<T> = {
  /** true: no muestra "Cargando…" (se usa para refrescar la lista tras guardar). */
  silent?: boolean;
  /** Pide los datos con otra función solo esta vez (por ejemplo, con otros filtros). */
  fetcher?: () => Promise<T>;
};

export type UseFetchResult<T> = {
  data: T;
  loading: boolean;
  /** Mensaje del último error, o '' si no hay. */
  error: string;
  /** false cuando reintentar no ayuda (un 4xx: permisos, datos inexistentes…). */
  retryable: boolean;
  reload: (options?: ReloadOptions<T>) => Promise<void>;
};

/**
 * Carga de datos de una pantalla: pide al montar, expone carga y error, y deja volver a pedir con `reload`.
 *
 * - No reintenta solo: un error se muestra y el usuario decide (botón "Reintentar").
 * - Si el componente se desmonta, la respuesta que llegue después se descarta.
 * - Si hay varias peticiones en vuelo (por ejemplo, dos búsquedas seguidas), vale solo la última.
 *
 * @param fetcher Función que pide los datos. Se usa siempre la de la última renderización.
 * @param initial Valor mientras no hay datos.
 * @param mensajeError Texto cuando el error no trae un mensaje propio (por ejemplo, sin conexión).
 */
export function useFetch<T> (fetcher: () => Promise<T>, initial: T, mensajeError: string): UseFetchResult<T> {
  const [data, setData] = useState<T>(initial);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [retryable, setRetryable] = useState(true);

  const fetcherRef = useRef(fetcher);
  const mensajeRef = useRef(mensajeError);
  const montado = useRef(false);
  const ultimaPeticion = useRef(0);

  useEffect(() => {
    fetcherRef.current = fetcher;
    mensajeRef.current = mensajeError;
  });

  const reload = useCallback(async (options: ReloadOptions<T> = {}) => {
    const numero = ++ultimaPeticion.current;
    if (!options.silent) setLoading(true);
    setError('');
    try {
      const resultado = await (options.fetcher ?? fetcherRef.current)();
      if (!montado.current || numero !== ultimaPeticion.current) return;
      setData(resultado);
      setError('');
    } catch (err) {
      if (!montado.current || numero !== ultimaPeticion.current) return;
      setError(getErrorMessage(err, mensajeRef.current));
      setRetryable(!(isApiError(err) && err.isClientError));
    } finally {
      if (montado.current && numero === ultimaPeticion.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    montado.current = true;
    reload().catch(() => {}); // reload ya guarda el error en el estado
    return () => { montado.current = false; };
  }, [reload]);

  return { data, loading, error, retryable, reload };
}
