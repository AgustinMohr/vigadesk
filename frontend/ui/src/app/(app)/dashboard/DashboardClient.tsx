// ==============================================================
// DASHBOARD (Client Component) — resumen general del sistema
// --------------------------------------------------------------
// Pantalla de inicio que resume el estado global: cuenta de clientes,
// obras, productos y pedidos, distribución de pedidos por estado,
// total acumulado en pesos y productos con stock bajo.
//
// ALCANCE POR ROL: si la sesión es CLIENTE, las estadísticas de pedidos
// (tarjeta "Pedidos" + panel "Pedidos por estado") se acotan a SU empresa
// vía search({ clienteId }), igual que en PedidosPageClient. El resto de
// las tarjetas (clientes/obras/productos y stock bajo) sigue siendo el
// resumen global, igual para todos los roles.
//
// PARTICULARIDAD CLAVE: a diferencia de ObrasPageClient (que hace un
// fetch por entidad de forma secuencial), acá se usa `Promise.all`
// para lanzar los 4 fetches A LA VEZ en paralelo y esperar a que TODOS
// terminen juntos. Esto es más rápido porque las peticiones se
// superponen en vez de esperarse en cadena.
//
// CONCEPTOS CLAVE: Promise.all (paralelismo), useEffect con retries,
// patrón "cancelled", cálculos derivados en cada render (sin useMemo
// porque son baratos), render condicional, formateo de moneda local,
// modal condicional (StockBajoModal).
//
// NOTA: los cálculos de estadísticas (pedidosPorEstado, totalPedidos,
// productosStockBajo) se recalculan en cada render. Al ser operaciones
// de bajo costo sobre listas pequeñas, usar useMemo sería optimización
// prematura: el costo de recalcular es menor al costo del memo.
// ==============================================================

'use client';

import { useEffect, useState } from 'react';
import { clienteService, type Cliente } from '@/services/clienteService';
import { obraService, type Obra } from '@/services/obraService';
import {
  productoService,
  type Producto,
} from '@/services/productoService';
import { pedidoService, type Pedido } from '@/services/pedidoService';
import StockBajoModal from '@/components/StockBajoModal';
import { useAuth } from '@/lib/auth/AuthContext';

// Mapa de clases de estilo para la "pill" (etiqueta redondeada) de cada
// estado de pedido. La clave es el estado y el valor son clases Tailwind
// de color de texto y fondo. `Record<string, string>` indica "objeto con
// claves string y valores string".
const estadoColors: Record<string, string> = {
  RECIBIDO: 'text-yellow-700 bg-yellow-100',
  ACEPTADO: 'text-blue-700 bg-blue-100',
  EN_PREPARACION: 'text-indigo-700 bg-indigo-100',
  ENTREGA_PARCIAL: 'text-purple-700 bg-purple-100',
  ENTREGADO: 'text-green-700 bg-green-100',
  CANCELADO: 'text-red-700 bg-red-100',
  RECHAZADO: 'text-gray-700 bg-gray-100',
};

/**
 * Formatea un número como precio argentino.
 *
 * `toLocaleString('es-AR', ...)` aplica el formato de moneda de Argentina:
 * separador de miles con punto y 2 decimales → "$1.234,56".
 *
 * @param {number} n - El valor numérico a formatear (ej: total de pedidos).
 * @returns {string} El número formateado con prefijo "$" y 2 decimales.
 */
function formatCurrency (n: number) {
  return `$${n.toLocaleString('es-AR', { minimumFractionDigits: 2 })}`;
}

/**
 * Componente del dashboard: carga las 4 entidades en paralelo y arma
 * las estadísticas de resumen. Es un Client Component porque necesita
 * estado y efectos para traer los datos tras el montaje.
 *
 * @returns {JSX.Element} La sección con tarjetas de conteo, panel de
 *   pedidos por estado, panel de stock bajo y el modal opcional.
 */
export default function DashboardClient () {
  // Sesión: el rol decide el alcance de los datos. Si es CLIENTE, las
// estadísticas de pedidos se acotan a SU empresa (clienteId); ADMIN ve el
// resumen global. GUEST no accede a datos operativos.
  const { user } = useAuth();
  const clienteId = user?.rol === 'CLIENTE' ? user.clienteId : undefined;
  const isGuest = user?.rol === 'GUEST';

  // --------------------------------------------------------------
  // useState: una lista de estado por entidad + flags de UI.
  // Cuando cada setter actualiza su lista, React re-renderiza y los
  // cálculos derivados de abajo se vuelven a ejecutar.
  // --------------------------------------------------------------
  const [clientes, setClientes] = useState<Cliente[]>([]);
  const [obras, setObras] = useState<Obra[]>([]);
  const [productos, setProductos] = useState<Producto[]>([]);
  const [pedidos, setPedidos] = useState<Pedido[]>([]);
  const [loading, setLoading] = useState(true);
  // Controla la visibilidad del StockBajoModal (los más de 10 ítems).
  const [showAllStockBajo, setShowAllStockBajo] = useState(false);

  // --------------------------------------------------------------
  // useEffect: al montar, carga las 4 entidades en paralelo.
  // `Promise.all([...])` recibe un array de promesas y resuelve con un
  // array con sus resultados en el mismo orden. Si UNA falla, toda la
  // promesa se rechaza y caemos en el catch (retry a los 5s).
  // La bandera `cancelled` evita setState tras desmontar (patrón estándar).
  // El array `[]` = "ejecutar solo una vez al montar".
  // --------------------------------------------------------------
  useEffect(() => {
    if (isGuest) {
      setLoading(false);
      return;
    }
    let cancelled = false;
    const fetchAll = async () => {
      try {
        // Desestructuración del array resultante en 4 variables. Los 4
        // fetches se lanzan al mismo tiempo (paralelo), no en serie. El de
        // pedidos se filtra por clienteId si hay sesión CLIENTE (solo su
        // empresa); si no, trae todos (admin/guest).
        const [c, o, p, pe] = await Promise.all([
          clienteService.getAll(),
          obraService.getAll(),
          productoService.getAll(),
          clienteId != null
            ? pedidoService.search({ clienteId })
            : pedidoService.getAll(),
        ]);
        if (!cancelled) {
          setClientes(c);
          setObras(o);
          setProductos(p);
          setPedidos(pe);
          setLoading(false);
        }
      } catch {
        // Retry simple: si algo falló, reintenta todo en 5 segundos.
        if (!cancelled) setTimeout(fetchAll, 5000);
      }
    };
    fetchAll();
    // Cleanup al desmontar: marca la bandera para que el fetch pendiente
    // no intente actualizar un componente que ya no existe.
    return () => { cancelled = true; };
  }, [isGuest]);

  // --------------------------------------------------------------
  // Cálculos "derivados": se recomputan en CADA render (son baratos).
  //  1. `pedidosPorEstado`: objeto { estado: cantidad } recorriendo la
  //     lista fija de estados y contando con `filter().length`.
  //  2. `totalPorEstado`: objeto { estado: suma } con la suma de los
  //     totales de los pedidos de cada estado (reduce sobre el filter).
  //  3. `totalPedidos`: suma acumulada de todos los totales de pedidos.
  //  4. `productosStockBajo`: productos cuyo stock actual es menor al mínimo.
  // --------------------------------------------------------------
  const estadosPedido = ['RECIBIDO', 'ACEPTADO', 'EN_PREPARACION', 'ENTREGA_PARCIAL', 'ENTREGADO', 'CANCELADO', 'RECHAZADO'];
  // `Object.fromEntries` convierte un array de pares [clave, valor] en un
  // objeto: [[est, count], ...] → { est: count }. Es el patrón ideal para
  // armar un "diccionario" de conteos por estado.
  const pedidosPorEstado = Object.fromEntries(
    estadosPedido.map((est) => [est, pedidos.filter((p) => p.estado === est).length])
  );
  // Suma de los totales por estado: para cada estado se suman los `total`
  // de sus pedidos con `reduce` (el 0 es el acumulador inicial).
  const totalPorEstado = Object.fromEntries(
    estadosPedido.map((est) => [
      est,
      pedidos.filter((p) => p.estado === est).reduce((sum, p) => sum + (p.total ?? 0), 0),
    ])
  );
  // `reduce` acumula un valor a lo largo del array. El segundo argumento
  // (0) es el valor inicial del acumulador `sum`.
  const totalPedidos = pedidos.reduce((sum, p) => sum + (p.total ?? 0), 0);
  const productosStockBajo = productos.filter((p) => p.stockActual < p.stockMinimo);

  // Render condicional temprano: mientras carga, devolvemos una vista
  // reducida y NO seguimos ejecutando el JSX completo.
  if (loading) {
    return (
      <section>
        <h2 className='text-2xl font-semibold'>Dashboard</h2>
        <p className='mt-2 text-slate-600'>Resumen general del sistema.</p>
        <p className='mt-8 text-slate-400'>Cargando información…</p>
      </section>
    );
  }

  if (isGuest) {
    return (
      <section>
        <h2 className='text-2xl font-semibold'>Dashboard</h2>
        <p className='mt-2 text-slate-600'>
          Tu usuario invitado solo tiene acceso al catálogo de productos.
        </p>
      </section>
    );
  }

  return (
    <section>
      <h2 className='text-2xl font-semibold'>Dashboard</h2>
      <p className='mt-2 text-slate-600'>Resumen general del sistema.</p>

      {/* Tarjetas: muestran el `.length` (cantidad de ítems) de cada
          lista ya cargada. */}
      <div className='mt-6 grid grid-cols-2 lg:grid-cols-4 gap-4'>
        <div className='rounded-lg border border-slate-200 bg-white p-5'>
          <p className='text-sm font-medium text-slate-500'>Clientes</p>
          <p className='mt-1 text-3xl font-semibold text-slate-900'>
            {clientes.length}
          </p>
        </div>
        <div className='rounded-lg border border-slate-200 bg-white p-5'>
          <p className='text-sm font-medium text-slate-500'>Obras</p>
          <p className='mt-1 text-3xl font-semibold text-slate-900'>
            {obras.length}
          </p>
        </div>
        <div className='rounded-lg border border-slate-200 bg-white p-5'>
          <p className='text-sm font-medium text-slate-500'>Productos</p>
          <p className='mt-1 text-3xl font-semibold text-slate-900'>
            {productos.length}
          </p>
        </div>
        <div className='rounded-lg border border-slate-200 bg-white p-5'>
          <p className='text-sm font-medium text-slate-500'>Pedidos</p>
          <p className='mt-1 text-3xl font-semibold text-slate-900'>
            {pedidos.length}
          </p>
        </div>
      </div>

      <div className='mt-6 grid grid-cols-1 lg:grid-cols-2 gap-6'>
        {/* Panel izquierdo: pedidos agrupados por estado con su suma
            acumulada + total general de todos los pedidos.
            Cada estado se pinta con su "pill" de color (estadoColors). */}
        <div className='rounded-lg border border-slate-200 bg-white p-5'>
          <h3 className='text-sm font-semibold text-slate-700'>
            Pedidos por estado
          </h3>
          <div className='mt-3 space-y-2'>
            {/* `.map` transforma cada estado en un renglón de la lista.
                `key={est}` es obligatorio para que React identifique cada
                fila al re-renderizar. Cada fila muestra la cantidad de
                pedidos y la suma de sus totales. */}
            {estadosPedido.map((est) => (
              <div
                key={est}
                className='flex items-center justify-between gap-2 text-sm'
              >
                <div className='flex items-center gap-2'>
                  <span
                    className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${
                      // `??` da un color por defecto si el estado no está en el mapa.
                      estadoColors[est] ?? 'text-slate-700 bg-slate-100'
                    }`}
                  >
                    {est}
                  </span>
                  {/* Cantidad de pedidos en ese estado (texto tenue). */}
                  <span className='text-xs text-slate-400'>
                    {pedidosPorEstado[est] === 1 ? '1 pedido' : `${pedidosPorEstado[est]} pedidos`}
                  </span>
                </div>
                {/* Suma de los totales de los pedidos de ese estado. */}
                <span className='font-medium text-slate-900'>
                  {formatCurrency(totalPorEstado[est])}
                </span>
              </div>
            ))}
          </div>
          <hr className='my-3 border-slate-200' />
          {/* Suma de TODOS los totales de pedidos, formateada con formatCurrency. */}
          <div className='flex items-center justify-between text-sm'>
            <span className='font-medium text-slate-500'>Total de todos los pedidos</span>
            <span className='text-lg font-semibold text-slate-900'>
              {formatCurrency(totalPedidos)}
            </span>
          </div>
        </div>

        {/* Panel derecho: productos con stock actual menor al mínimo.
            Muestra solo los primeros 10; si hay más, un botón abre el modal. */}
        <div className='rounded-lg border border-slate-200 bg-white p-5'>
          <h3 className='text-sm font-semibold text-slate-700'>
            Productos con stock bajo
          </h3>
          {/* Render condicional: sin productos → mensaje de éxito; con
              productos → lista (slice de 10) + botón "Ver más". */}
          {productosStockBajo.length === 0
            ? (
              <p className='mt-3 text-sm text-slate-400'>
                Todos los productos tienen stock suficiente.
              </p>
              )
            : (
              <div className='mt-3 space-y-2'>
                {/* `slice(0, 10)` acota la lista a los primeros 10. */}
                {productosStockBajo.slice(0, 10).map((p) => (
                  <div
                    key={p.id}
                    className='flex items-center justify-between rounded-md bg-slate-50 px-3 py-2 text-sm'
                  >
                    <span className='text-slate-900'>{p.nombre}</span>
                    <span className='text-red-600 font-medium'>
                      {p.stockActual} / {p.stockMinimo}
                    </span>
                  </div>
                ))}
                {/* Solo si hay más de 10, mostramos el botón que abre el
                    modal con el listado completo. */}
                {productosStockBajo.length > 10 && (
                  <div className='pt-2 text-center'>
                    <button
                      onClick={() => setShowAllStockBajo(true)}
                      className='text-sm font-medium text-slate-600 underline underline-offset-2 hover:text-slate-900'
                    >
                      Ver más ({productosStockBajo.length - 10} restantes)
                    </button>
                  </div>
                )}
              </div>
              )}
        </div>

        {/* Modal: se monta solo si showAllStockBajo es true (render
            condicional). onClose resetea el estado para ocultarlo. */}
        {showAllStockBajo && (
          <StockBajoModal
            productos={productosStockBajo}
            onClose={() => setShowAllStockBajo(false)}
          />
        )}
      </div>
    </section>
  );
}
