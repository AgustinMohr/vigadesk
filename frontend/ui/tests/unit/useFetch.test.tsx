import { act, renderHook, screen, waitFor } from '@testing-library/react';
import { render } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import FetchError from '@/components/FetchError';
import { ApiError } from '@/lib/http/api-error';
import { useFetch } from '@/hooks/useFetch';

function diferido<T> () {
  let resolver!: (valor: T) => void;
  let rechazar!: (error: unknown) => void;
  const promesa = new Promise<T>((resolve, reject) => { resolver = resolve; rechazar = reject; });
  return { promesa, resolver, rechazar };
}

describe('useFetch', () => {
  it('arranca cargando y entrega los datos', async () => {
    const { result } = renderHook(() => useFetch(() => Promise.resolve(['a', 'b']), [] as string[], 'fallo'));

    expect(result.current.loading).toBe(true);
    expect(result.current.data).toEqual([]);

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.data).toEqual(['a', 'b']);
    expect(result.current.error).toBe('');
  });

  it('un error de la API muestra su mensaje y no reintenta solo', async () => {
    vi.useFakeTimers();
    const fetcher = vi.fn().mockRejectedValue(new ApiError(503, 'El servicio está temporalmente fuera de servicio.'));
    const { result } = renderHook(() => useFetch(fetcher, [] as string[], 'respaldo'));

    await act(async () => { await vi.advanceTimersByTimeAsync(60_000); });

    expect(result.current.error).toBe('El servicio está temporalmente fuera de servicio.');
    expect(result.current.loading).toBe(false);
    expect(result.current.retryable).toBe(true);
    expect(fetcher).toHaveBeenCalledTimes(1); // ni a los 5 s ni al minuto
    vi.useRealTimers();
  });

  it('un error que no es de la API usa el mensaje de respaldo', async () => {
    const { result } = renderHook(() => useFetch(() => Promise.reject(new Error('boom')), [] as string[], 'No se pudo cargar.'));

    await waitFor(() => expect(result.current.error).toBe('No se pudo cargar.'));
    expect(result.current.retryable).toBe(true);
  });

  it.each([400, 403, 404, 422])('un %i no es reintentable', async (status) => {
    const { result } = renderHook(() => useFetch(() => Promise.reject(new ApiError(status, 'no')), [] as string[], 'x'));

    await waitFor(() => expect(result.current.error).toBe('no'));
    expect(result.current.retryable).toBe(false);
  });

  it('reload despues de un error vuelve a pedir y limpia el error', async () => {
    const fetcher = vi.fn()
      .mockRejectedValueOnce(new ApiError(502, 'caido'))
      .mockResolvedValueOnce(['ok']);
    const { result } = renderHook(() => useFetch(fetcher, [] as string[], 'x'));
    await waitFor(() => expect(result.current.error).toBe('caido'));

    await act(async () => { await result.current.reload(); });

    expect(result.current.error).toBe('');
    expect(result.current.data).toEqual(['ok']);
    expect(fetcher).toHaveBeenCalledTimes(2);
  });

  it('reload silencioso no muestra "cargando"', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(['uno']);
    const { result } = renderHook(() => useFetch(fetcher, [] as string[], 'x'));
    await waitFor(() => expect(result.current.data).toEqual(['uno']));

    const segunda = diferido<string[]>();
    fetcher.mockReturnValueOnce(segunda.promesa);
    let recarga!: Promise<void>;
    act(() => { recarga = result.current.reload({ silent: true }); });

    expect(result.current.loading).toBe(false);
    await act(async () => { segunda.resolver(['tres']); await recarga; });
    expect(result.current.data).toEqual(['tres']);
  });

  it('reload con otra funcion la usa solo esa vez', async () => {
    const fetcher = vi.fn().mockResolvedValue(['base']);
    const { result } = renderHook(() => useFetch(fetcher, [] as string[], 'x'));
    await waitFor(() => expect(result.current.data).toEqual(['base']));

    await act(async () => { await result.current.reload({ fetcher: () => Promise.resolve(['filtrado']) }); });

    expect(result.current.data).toEqual(['filtrado']);
    expect(fetcher).toHaveBeenCalledTimes(1);
  });

  it('usa la funcion de la ultima renderizacion al recargar (filtros actuales)', async () => {
    let filtro = 'a';
    const { result, rerender } = renderHook(() => useFetch(() => Promise.resolve([filtro]), [] as string[], 'x'));
    await waitFor(() => expect(result.current.data).toEqual(['a']));

    filtro = 'b';
    rerender();
    await act(async () => { await result.current.reload(); });

    expect(result.current.data).toEqual(['b']);
  });

  it('descarta la respuesta si el componente se desmonto', async () => {
    const pedido = diferido<string[]>();
    const { result, unmount } = renderHook(() => useFetch(() => pedido.promesa, [] as string[], 'x'));
    const estadoAntes = result.current;

    unmount();
    await act(async () => { pedido.resolver(['tarde']); await pedido.promesa; });

    expect(result.current).toBe(estadoAntes); // no hubo actualizacion de estado tras desmontar
  });

  it('si hay dos peticiones en vuelo, vale solo la ultima', async () => {
    const lenta = diferido<string[]>();
    const rapida = diferido<string[]>();
    const fetcher = vi.fn().mockReturnValueOnce(lenta.promesa).mockReturnValueOnce(rapida.promesa);
    const { result } = renderHook(() => useFetch(fetcher, [] as string[], 'x'));

    let segunda!: Promise<void>;
    act(() => { segunda = result.current.reload(); });
    await act(async () => { rapida.resolver(['nueva']); await segunda; });
    await act(async () => { lenta.resolver(['vieja']); await lenta.promesa; });

    expect(result.current.data).toEqual(['nueva']);
  });
});

describe('FetchError', () => {
  it('muestra el mensaje y el boton Reintentar que llama a onRetry', async () => {
    const onRetry = vi.fn();
    render(<FetchError mensaje='No se pudieron cargar los clientes.' onRetry={onRetry} />);

    expect(screen.getByRole('alert')).toHaveTextContent('No se pudieron cargar los clientes.');
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it('sin onRetry solo muestra el mensaje', () => {
    render(<FetchError mensaje='No tenés permisos para realizar esta acción.' />);

    expect(screen.getByRole('alert')).toHaveTextContent('No tenés permisos');
    expect(screen.queryByRole('button')).toBeNull();
  });
});
