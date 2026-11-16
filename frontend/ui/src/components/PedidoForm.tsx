// ==============================================================
// FORMULARIO DE PEDIDO (modal crear)
// --------------------------------------------------------------
// Mismo patrón que ObraForm.tsx (inputs controlados + onSubmit) con
// varias particularidades complejas:
//
// 1. DATOS EXTERNOS: carga clientes, productos y OBRAS del cliente desde
//    la API (useEffect de montaje, seguido de clienteService/productoService
//    y obraService.getByClienteId). Para el pedido solo se ofrecen las
//    obras HABILITADAS del cliente (no se crean pedidos a obras viejas).
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
import { obraService, type Obra } from '@/services/obraService';
import { useAuth } from '@/lib/auth/AuthContext';
import Button from './Button';
import { getErrorMessage } from '@/lib/http/api-error';

/**
 * Una línea del pedido: producto + cantidad, con datos desnormalizados
 * (nombre y precio se copian del producto elegido para mostrar en vivo
 * sin tener que buscar en la lista en el render).
 *
 * @typedef {Object} LineItem
 * @property {number} productoId - Id del producto (0 = ninguno elegido aún).
 * @property {string} productoNombre - Nombre copiado del producto (display).
 * @property {number} precio - Precio unitario copiado del producto.
 * @property {number} descuento - Descuento promocional (%) del producto.
 * @property {number} cantidad - Cantidad de unidades de esta línea.
 */
type LineItem = {
  productoId: number;
  productoNombre: string;
  unidadMedida: string;
  precio: number;
  descuento: number;
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
  onSubmit: (data: Partial<Pedido>, idempotencyKey: string) => Promise<void>;
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

  // Clave estable para que un reintento del mismo formulario no duplique el pedido.
  const [idempotencyKey] = useState(() => crypto.randomUUID());

  const [observaciones, setObservaciones] = useState('');
  // Obras del cliente seleccionado (solo HABILITADAS) y la elegida en el
  // form. `obraId` vacío = pedido sin obra (el modelo lo permite).
  const [obras, setObras] = useState<Obra[]>([]);
  const [obraId, setObraId] = useState('');
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
    // Los productos los necesitan todos los roles. Se ordenan ALFABÉTICAMENTE
    // por nombre para que el select del pedido sea fácil de recorrer; el spread
    // `[...]` copia el array antes de ordenar (no mutar la respuesta).
    productoService.getAll()
      .then((data) =>
        setProductos([...data].sort((a, b) => a.nombre.localeCompare(b.nombre, 'es')))
      )
      .catch(() => {});
  }, []);

  // Id del cliente "efectivo" del pedido:
  //  - CLIENTE → el de su sesión (fijo).
  //  - Admin → el elegido en el <select> de cliente ('' → null).
  const selectedClienteNumeric =
    isCliente && user?.clienteId != null
      ? user.clienteId
      : clienteId
        ? Number(clienteId)
        : null;

  // Cuando cambia el cliente, carga sus obras HABILITADAS y resetea la obra
  // elegida (una obra de otra empresa no debe quedar seleccionada).
  useEffect(() => {
    setObraId('');
    setObras([]);
    if (selectedClienteNumeric == null) return;

    obraService.getByClienteId(selectedClienteNumeric)
      .then((data) => setObras(data.filter((o) => o.estado === 'HABILITADA')))
      .catch(() => setObras([]));
  }, [selectedClienteNumeric]);

  // TOTAL EN VIVO: `reduce` acumula el subtotal NETO línea por línea, con
  // el mismo criterio que el backend: bruto = precio × cantidad, y si la
  // línea trae descuento válido (0 < % ≤ 100) se aplica (1 − descuento/100).
  // Se recalcula en cada render (siempre refleja el estado actual).
  const total = lineItems.reduce((sum, item) => {
    const bruto = item.precio * item.cantidad;
    const conDescuento =
      item.descuento > 0 && item.descuento <= 100
        ? bruto * (1 - item.descuento / 100)
        : bruto;
    return sum + conDescuento;
  }, 0);

  /**
   * Descuento de una línea en PESOS: bruto × (descuento% / 100).
   * Solo se considera si el % es válido (0 < % ≤ 100); si el dato viene
   * sucio (> 100), se ignora y el descuento es $0.
   */
  const lineDescuentoPesos = (item: LineItem): number => {
    const bruto = item.precio * item.cantidad;
    return item.descuento > 0 && item.descuento <= 100
      ? bruto * (item.descuento / 100)
      : 0;
  };

  /**
   * Subtotal neto de una línea = bruto − descuento en pesos.
   * Replica la fórmula del backend ms-pedidos (precio × cantidad × (1 − %/100)).
   */
  const lineSubtotal = (item: LineItem): number => {
    const bruto = item.precio * item.cantidad;
    return bruto - lineDescuentoPesos(item);
  };

  /**
   * Formatea un número como pesos argentinos con 2 decimales
   * (mismo criterio que el "Total" del pie del modal).
   */
  const formatPesos = (n: number): string =>
    `$${n.toLocaleString('es-AR', { minimumFractionDigits: 2 })}`;

  /**
   * Agrega una línea vacía al final. `[...lineItems, nuevo]`:
   * el spread COPIA el array actual y le agrega el nuevo elemento →
   * se setea un array flamante (inmutable), que React detecta por
   * referencia distinta para re-renderizar.
   */
  const handleAddLine = () => {
    setLineItems([...lineItems, {
      productoId: 0,
      productoNombre: '',
      unidadMedida: 'UNIDAD',
      precio: 0,
      descuento: 0,
      cantidad: 1,
    }]);
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
      unidadMedida: prod?.unidadMedida ?? 'UNIDAD',
      precio: prod?.precio ?? 0,
      // Copia el descuento promocional (%) para poder mostrarlo en el
      // preview y para que el payload coincida con el cálculo del backend.
      descuento: prod?.descuentoPromocional ?? 0,
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
    // `some()`: true si ALGUNA línea no tiene producto o cantidad <= 0.
    if (lineItems.some((item) => !item.productoId || item.cantidad <= 0)) {
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

    // Resuelve la obra elegida (opcional): el backend de pedidos guarda un
    // snapshot { id, direccion }; si no se eligió obra, el pedido queda sin ella.
    const obraElegida = obras.find((o) => o.id === Number(obraId));

    // Transforma las líneas del form (LineItem) al formato que espera el
    // backend (DetallePedido). El `!` final afirma que el producto existe
    // (ya validamos que productId sea válido más arriba).
    const detalle: DetallePedido[] = lineItems.map((item) => {
      const prod = productos.find((p) => p.id === item.productoId)!;
      return {
        lineaId: '',
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
        // Spread condicional: si hay obra elegida, se manda el snapshot
        // { id, direccion }; si no, no se agrega la propiedad.
        ...(obraElegida
          ? { obra: { id: obraElegida.id!, direccion: obraElegida.direccion } }
          : {}),
        observaciones: observaciones.trim() || undefined,
        detalle,
        // El backend asigna/calcula estos valores reales (nro de pedido,
        // total con descuentos, validación de estado). Se mandan valores
        // inocuos como "seed"; el backend es la fuente de verdad.
        numeroPedido: 0,
        total: 1,
        estado: 'RECIBIDO', // estado inicial del flujo de pedidos
      }, idempotencyKey);
    } catch (err) {
      setError(getErrorMessage(err, 'Ocurrió un error al crear el pedido.'));
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

          {/* Selector de Obra (opcional): solo las obras HABILITADAS del
              cliente del pedido. Deshabilitado hasta que haya cliente. */}
          <div>
            <label className='block text-sm font-medium text-slate-700'>
              Obra <span className='text-xs font-normal text-slate-400'>(opcional)</span>
            </label>
            <select
              value={obraId}
              onChange={(e) => setObraId(e.target.value)}
              disabled={selectedClienteNumeric == null}
              className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none disabled:bg-slate-100'
            >
              {selectedClienteNumeric == null
                ? (
                  <option value='' disabled>
                    Seleccioná un cliente para ver sus obras
                  </option>
                  )
                : obras.length === 0
                  ? (
                    <option value='' disabled>
                      Sin obras habilitadas para este cliente
                    </option>
                    )
                  : (
                    <>
                      <option value=''>Sin obra</option>
                      {obras.map((obra) => (
                        <option key={obra.id} value={obra.id}>
                          {obra.direccion}
                        </option>
                      ))}
                    </>
                    )}
            </select>
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
                <div className='w-28'>
                  <label className='block text-xs font-medium text-slate-500 mb-1'>
                    Cantidad ({item.unidadMedida})
                  </label>
                  <input
                    type='number'
                    min={0.001}
                    step={0.001}
                    value={item.cantidad}
                    onChange={(e) => handleLineCantidadChange(i, Number(e.target.value))}
                    className='w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
                  />
                </div>
                {/* Desglose de la línea, en vivo: precio del producto,
                    descuento aplicado en PESOS (% sobre el bruto) y el
                    total neto final. Solo se muestra si hay producto. */}
                <div className='w-44 text-right text-xs text-slate-600 py-2 space-y-0.5'>
                  {item.productoId > 0
                    ? (
                      <>
                        <div>
                          P. unitario: <span className='font-medium text-slate-900'>{formatPesos(item.precio)}</span>
                        </div>
                        {lineDescuentoPesos(item) > 0 && (
                          <div className='text-emerald-700'>
                            Descuento: −{formatPesos(lineDescuentoPesos(item))} ({item.descuento}%)
                          </div>
                        )}
                        <div className='text-sm font-semibold text-slate-900'>
                          Total: {formatPesos(lineSubtotal(item))}
                        </div>
                      </>
                      )
                    : (
                      <div>—</div>
                      )}
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
