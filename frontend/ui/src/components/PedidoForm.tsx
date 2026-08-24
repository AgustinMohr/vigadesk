// ==============================================================
// FORMULARIO DE PEDIDO (modal crear)
// --------------------------------------------------------------
// Mismo patrón que ObraForm.tsx (inputs controlados + onSubmit) con
// varias particularidades complejas:
//
// 1. DATOS EXTERNOS: carga clientes y productos desde la API al montarse
//    (useEffect de montaje, seguido de clienteService/productoService).
//
// 2. LÍNEAS DINÁMICAS: un pedido tiene N productos. Cada línea vive en
//    `lineItems` (array de objetos LineItem) y se agrega/quita en vivo.
//    ES UN ARRAY EN EL ESTADO → para "mutarlo" SIEMPRE se crea una copia
//    nueva (inmutabilidad) y se setea: `setLineItems([...lineItems, …])`.
//
// 3. TOTAL EN VIVO: `reduce` recorre todas las líneas sumando
//    precio × cantidad. Se recalcula en CADA render (no usa useMemo,
//    porque es un cálculo barato).
//
// 4. ROL DEL USUARIO: si es CLIENTE, el <select> de cliente y el input de
//    usuario se deshabilitan — la empresa y el nombre se autocompletan
//    desde la sesión (useAuth). Esto FIXEA la empresa: un cliente no
//    puede cargar pedidos a nombre de otra empresa.
//
// CONCEPTOS CLAVE: inmutabilidad de arrays en React (spread operator),
// `reduce` para agregaciones, `.find()` para resolver ids → objetos,
// `some()` para validar la lista, key de índices en listas dinámicas.
// ==============================================================

'use client';

import { useEffect, useState } from 'react';
import type { Pedido, ClienteInfo, DetallePedido } from '@/services/pedidoService';
import type { Producto } from '@/services/productoService';
import { clienteService } from '@/services/clienteService';
import { productoService } from '@/services/productoService';
import { useAuth } from '@/lib/auth/AuthContext';
import Button from './Button';

/**
 * Una línea del pedido: producto + cantidad, con datos desnormalizados
 * (nombre y precio se copian del producto elegido para mostrar en vivo
 * sin tener que buscar en la lista en el render).
 *
 * @typedef {Object} LineItem
 * @property {number} productoId - Id del producto (0 = ninguno elegido aún).
 * @property {string} productoNombre - Nombre copiado del producto (display).
 * @property {number} precio - Precio unitario copiado del producto.
 * @property {number} cantidad - Cantidad de unidades de esta línea.
 */
type LineItem = {
  productoId: number;
  productoNombre: string;
  precio: number;
  cantidad: number;
};

/**
 * PROPS del modal de creación de pedido.
 *
 * @typedef {Object} PedidoFormProps
 * @property {(data: Partial<Pedido>) => Promise<void>} onSubmit - Callback
 *   con el pedido armado; el padre persiste y cierra el modal.
 * @property {() => void} onCancel - Cierra el modal sin guardar.
 */
type PedidoFormProps = {
  onSubmit: (data: Partial<Pedido>) => Promise<void>;
  onCancel: () => void;
};

/**
 * Modal de formulario para CREAR un pedido (solo creación: no edita).
 *
 * Flujo: carga catálogos → el usuario arma líneas con productos/cantidades
 * → se calcula el total en vivo → onSubmit envía el pedido con estado
 * inicial 'RECIBIDO'. Para usuarios con rol CLIENTE, la empresa y el
 * usuario quedan fijos (tomados de la sesión).
 *
 * @param {PedidoFormProps} props - Ver PedidoFormProps.
 * @returns {JSX.Element} El modal con el formulario de pedido.
 */
export default function PedidoForm ({ onSubmit, onCancel }: PedidoFormProps) {
  const { user } = useAuth();

  // Un usuario de cliente opera siempre en nombre de su propia empresa:
  // por eso sus campos se fijan y deshabilitan más abajo.
  const isCliente = user?.rol === 'CLIENTE';

  // Catálogos cargados desde la API (clientes y productos).
  const [clientes, setClientes] = useState<ClienteInfo[]>([]);
  const [productos, setProductos] = useState<Producto[]>([]);

  // Si es CLIENTE, su empresa ya viene de la sesión (user.clienteId) y el
  // select se deshabilita; si no, arranca vacío para que el admin elija.
  const [clienteId, setClienteId] = useState(
    isCliente ? String(user?.clienteId ?? '') : ''
  );

  // Mismo criterio para el usuario del pedido.
  const [usuario, setUsuario] = useState(isCliente ? user?.nombre ?? '' : '');

  const [observaciones, setObservaciones] = useState('');
  // Las líneas del pedido: el corazón de este componente.
  const [lineItems, setLineItems] = useState<LineItem[]>([]);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // Efecto de montaje: precarga los catálogos una vez.
  useEffect(() => {
    // Un CLIENTE no necesita la lista de clientes (su empresa está fija).
    if (!isCliente) {
      // Adapatación de forma: el service devuelve Cliente[] y el form solo
      // necesita id+nombre (ClienteInfo). `.map()` transforma el array —
      // crea un array NUEVO con la forma reducida.
      clienteService.getAll().then((data) => {
        // `c.id!` — non-null assertion: los ids de los listados siempre vienen.
        setClientes(data.map((c) => ({ id: c.id!, nombre: c.nombre })));
      }).catch(() => {});
    }
    // Los productos los necesitan todos los roles.
    productoService.getAll().then(setProductos).catch(() => {});
  }, []);

  // TOTAL EN VIVO: `reduce` acumula la suma precio × cantidad línea por
  // línea. Empieza en 0 y, para cada item, suma su subtotal. Se recalcula
  // en cada render (siempre refleja el estado actual de lineItems).
  const total = lineItems.reduce((sum, item) => sum + item.precio * item.cantidad, 0);

  /**
   * Agrega una línea vacía al final. `[...lineItems, nuevo]`:
   * el spread COPIA el array actual y le agrega el nuevo elemento →
   * se setea un array flamante (inmutable), que React detecta por
   * referencia distinta para re-renderizar.
   */
  const handleAddLine = () => {
    setLineItems([...lineItems, { productoId: 0, productoNombre: '', precio: 0, cantidad: 1 }]);
  };

  /**
   * Quita la línea del índice dado. `filter` devuelve un NUEVO array con
   * todos los elementos EXCEPTO el que cumple `i !== index` (invertimos
   * la condición). La línea removida queda en el array original que se
   * descarta (inmutable).
   */
  const handleRemoveLine = (index: number) => {
    setLineItems(lineItems.filter((_, i) => i !== index));
  };

  /**
   * Al elegir el producto de una línea, copia nombre y precio del catálogo
   * a la línea. MUTACIÓN CONTROLLED: copiamos el array con spread y
   * asignamos el objeto nuevo en la posición index (el objeto de la línea
   * también es nuevo → nada del estado original se toca).
   */
  const handleLineProductoChange = (index: number, productoId: number) => {
    const prod = productos.find((p) => p.id === productoId); // id → producto
    const newItems = [...lineItems];
    newItems[index] = {
      productoId,
      productoNombre: prod?.nombre ?? '',
      precio: prod?.precio ?? 0,
      cantidad: newItems[index].cantidad, // conserva la cantidad que ya estaba
    };
    setLineItems(newItems);
  };

  /**
   * Actualiza solo la cantidad de la línea index. `{ ...newItems[index], cantidad }`
   * — spread del objeto: copia TODAS las propiedades de la línea y pisa
   * únicamente `cantidad` (no perdemos producto/precio al editar).
   */
  const handleLineCantidadChange = (index: number, cantidad: number) => {
    const newItems = [...lineItems];
    newItems[index] = { ...newItems[index], cantidad };
    setLineItems(newItems);
  };

  /**
   * Valida el pedido completo y arma el payload para el padre.
   */
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!clienteId) {
      setError('Debe seleccionar un cliente.');
      return;
    }
    if (lineItems.length === 0) {
      setError('Debe agregar al menos un producto.');
      return;
    }
    // `some()`: true si ALGUNA línea no tiene producto o cantidad < 1.
    if (lineItems.some((item) => !item.productoId || item.cantidad < 1)) {
      setError('Complete todos los productos con cantidad válida.');
      return;
    }

    // Resuelve el cliente final:
    //  - Admin: lo encuentra en `clientes` por el id del select.
    //  - CLIENTE: el select estaba fijo con su empresa → la toma de la sesión.
    const cliente =
      clientes.find((c) => c.id === Number(clienteId)) ??
      (isCliente && user?.clienteId === Number(clienteId)
        ? { id: user.clienteId!, nombre: user.clienteNombre! }
        : undefined);
    if (!cliente) {
      setError('Cliente inválido.');
      return;
    }

    // Transforma las líneas del form (LineItem) al formato que espera el
    // backend (DetallePedido). El `!` final afirma que el producto existe
    // (ya validamos que productId sea válido más arriba).
    const detalle: DetallePedido[] = lineItems.map((item) => {
      const prod = productos.find((p) => p.id === item.productoId)!;
      return {
        producto: {
          id: prod.id!,
          nombre: prod.nombre,
          precio: prod.precio,
          // Se copia el descuento promocional para que el backend calcule
          // el precioFinal de la línea y el total aplicando la promoción.
          descuentoPromocional: prod.descuentoPromocional,
        },
        cantidad: item.cantidad,
      };
    });

    setSubmitting(true);
    try {
      await onSubmit({
        cliente: { id: cliente.id, nombre: cliente.nombre },
        usuario: usuario.trim() || undefined,
        observaciones: observaciones.trim() || undefined,
        detalle,
        // El backend asigna/calcula estos valores reales (nro de pedido,
        // total con descuentos, validación de estado). Se mandan valores
        // inocuos como "seed"; el backend es la fuente de verdad.
        numeroPedido: 0,
        total: 1,
        estado: 'RECIBIDO', // estado inicial del flujo de pedidos
      });
    } catch {
      setError('Ocurrió un error al crear el pedido.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    // Overlay de modal: fondo oscuro + caja blanca centrada. Está ventana
    // es más ancha (max-w-2xl) porque lleva las líneas de productos.
    <div className='fixed inset-0 z-50 flex items-center justify-center bg-black/40'>
      {/* `max-h-[90vh] overflow-y-auto`: si el pedido tiene muchas líneas,
          el modal hace scroll en vez de desbordar la pantalla. */}
      <div className='w-full max-w-2xl rounded-lg bg-white p-6 shadow-xl max-h-[90vh] overflow-y-auto'>
        <h3 className='mb-4 text-lg font-semibold text-slate-900'>Nuevo pedido</h3>

        <form onSubmit={handleSubmit} className='space-y-4'>
          <div>
            <label className='block text-sm font-medium text-slate-700'>
              Cliente <span className='text-red-500'>*</span>
            </label>
            {/* `disabled={isCliente}`: el CLIENTE tiene fija su empresa
                (no puede cargar pedidos para otra). disabled:bg-slate-100
                da el look "grisado" de campo no editable. */}
            <select
              value={clienteId}
              onChange={(e) => setClienteId(e.target.value)}
              disabled={isCliente}
              className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none disabled:bg-slate-100'
            >
              <option value=''>Seleccionar cliente</option>
              {isCliente
                ? (
                  // Para el CLIENTE SOLO existe su empresa como opción.
                  <option value={user?.clienteId}>{user?.clienteNombre}</option>
                  )
                : (
                    clientes.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.nombre}
                      </option>
                    ))
                  )}
            </select>
          </div>

          <div className='grid grid-cols-2 gap-4'>
            <div>
              <label className='block text-sm font-medium text-slate-700'>Usuario</label>
              {/* `readOnly={isCliente}`: el nombre del CLIENTE se
                  autocompleta de la sesión y no debe poder cambiarse. */}
              <input
                type='text'
                value={usuario}
                onChange={(e) => setUsuario(e.target.value)}
                readOnly={isCliente}
                className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none disabled:bg-slate-100'
              />
            </div>
            <div>
              <label className='block text-sm font-medium text-slate-700'>Observaciones</label>
              <input
                type='text'
                value={observaciones}
                onChange={(e) => setObservaciones(e.target.value)}
                className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
              />
            </div>
          </div>

          <div>
            <div className='flex items-center justify-between mb-2'>
              <label className='block text-sm font-medium text-slate-700'>
                Productos <span className='text-red-500'>*</span>
              </label>
              {/* type='button' evita que este botón dispare el submit del form */}
              <Button variant='secondary' onClick={handleAddLine} type='button'>
                + Agregar producto
              </Button>
            </div>

            {lineItems.length === 0 && (
              <p className='text-sm text-slate-400 py-2'>No hay productos agregados.</p>
            )}

            {/* Render de líneas dinámicas: `lineItems.map`. OJO: la `key={i}`
                usa el ÍNDICE (no un id único). Aceptable acá porque las
                líneas solo se agregan/al final o se quitan (orden estable);
                si se reordenaran, el índice sería un antipatrón. */}
            {lineItems.map((item, i) => (
              <div key={i} className='mb-2 flex items-end gap-2'>
                <div className='flex-1'>
                  <label className='block text-xs font-medium text-slate-500 mb-1'>Producto</label>
                  {/* Select de producto de la línea; onChange dispara la
                      copia de nombre/precio a la línea */}
                  <select
                    value={item.productoId}
                    onChange={(e) => handleLineProductoChange(i, Number(e.target.value))}
                    className='w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
                  >
                    {/* value={0} = placeholder "ninguno elegido" */}
                    <option value={0}>Seleccionar</option>
                    {productos.map((p) => (
                      <option key={p.id} value={p.id!}>
                        {p.nombre} (${Number(p.precio).toLocaleString('es-AR')})
                      </option>
                    ))}
                  </select>
                </div>
                <div className='w-24'>
                  <label className='block text-xs font-medium text-slate-500 mb-1'>Cantidad</label>
                  <input
                    type='number'
                    min={1}
                    value={item.cantidad}
                    onChange={(e) => handleLineCantidadChange(i, Number(e.target.value))}
                    className='w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
                  />
                </div>
                {/* Subtotal de la línea, en vivo (precio × cantidad) */}
                <div className='w-28 text-right text-sm text-slate-600 py-2'>
                  {item.precio > 0
                    ? `$${(item.precio * item.cantidad).toLocaleString('es-AR')}`
                    : '—'}
                </div>
                {/* Botón quitar línea: `&times;` es la entidad HTML "×" */}
                <button
                  type='button'
                  onClick={() => handleRemoveLine(i)}
                  className='text-red-500 hover:text-red-700 text-lg leading-none pb-1'
                >
                  &times;
                </button>
              </div>
            ))}
          </div>

          {/* Total del pedido, recalculado en cada cambio */}
          <div className='text-right text-base font-semibold text-slate-900'>
            Total: ${total.toLocaleString('es-AR', { minimumFractionDigits: 2 })}
          </div>

          {error && <p className='text-sm text-red-600'>{error}</p>}

          <div className='flex justify-end gap-2 pt-2'>
            <Button variant='secondary' onClick={onCancel} disabled={submitting}>
              Cancelar
            </Button>
            <Button type='submit' disabled={submitting}>
              {submitting ? 'Creando…' : 'Crear pedido'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
