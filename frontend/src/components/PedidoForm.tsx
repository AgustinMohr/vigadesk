"use client";

import { useEffect, useState } from "react";
import type { Pedido, ClienteInfo, DetallePedido } from "@/services/pedidoService";
import type { Producto } from "@/services/productoService";
import { clienteService } from "@/services/clienteService";
import { productoService } from "@/services/productoService";
import Button from "./Button";

type LineItem = {
  productoId: number;
  productoNombre: string;
  precio: number;
  cantidad: number;
};

// Modal formulario para crear un pedido
// Carga clientes y productos desde la API al montarse
// Permite agregar líneas de producto dinámicas con selector + cantidad
// Calcula el total automáticamente con la suma precio × cantidad
type PedidoFormProps = {
  onSubmit: (data: Partial<Pedido>) => Promise<void>;
  onCancel: () => void;
};

export default function PedidoForm({ onSubmit, onCancel }: PedidoFormProps) {
  const [clientes, setClientes] = useState<ClienteInfo[]>([]);
  const [productos, setProductos] = useState<Producto[]>([]);
  const [clienteId, setClienteId] = useState("");
  const [usuario, setUsuario] = useState("");
  const [observaciones, setObservaciones] = useState("");
  const [lineItems, setLineItems] = useState<LineItem[]>([]);
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    clienteService.getAll().then((data) => {
      setClientes(data.map((c) => ({ id: c.id!, nombre: c.nombre })));
    }).catch(() => {});
    productoService.getAll().then(setProductos).catch(() => {});
  }, []);

  const total = lineItems.reduce((sum, item) => sum + item.precio * item.cantidad, 0);

  const handleAddLine = () => {
    setLineItems([...lineItems, { productoId: 0, productoNombre: "", precio: 0, cantidad: 1 }]);
  };

  const handleRemoveLine = (index: number) => {
    setLineItems(lineItems.filter((_, i) => i !== index));
  };

  const handleLineProductoChange = (index: number, productoId: number) => {
    const prod = productos.find((p) => p.id === productoId);
    const newItems = [...lineItems];
    newItems[index] = {
      productoId,
      productoNombre: prod?.nombre ?? "",
      precio: prod?.precio ?? 0,
      cantidad: newItems[index].cantidad,
    };
    setLineItems(newItems);
  };

  const handleLineCantidadChange = (index: number, cantidad: number) => {
    const newItems = [...lineItems];
    newItems[index] = { ...newItems[index], cantidad };
    setLineItems(newItems);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");

    if (!clienteId) {
      setError("Debe seleccionar un cliente.");
      return;
    }
    if (lineItems.length === 0) {
      setError("Debe agregar al menos un producto.");
      return;
    }
    if (lineItems.some((item) => !item.productoId || item.cantidad < 1)) {
      setError("Complete todos los productos con cantidad válida.");
      return;
    }

    const cliente = clientes.find((c) => c.id === Number(clienteId));
    if (!cliente) {
      setError("Cliente inválido.");
      return;
    }

    const detalle: DetallePedido[] = lineItems.map((item) => {
      const prod = productos.find((p) => p.id === item.productoId)!;
      return {
        producto: { id: prod.id!, nombre: prod.nombre, precio: prod.precio },
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
        numeroPedido: 0,
        total: 1,
        estado: "RECIBIDO",
      });
    } catch {
      setError("Ocurrió un error al crear el pedido.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="w-full max-w-2xl rounded-lg bg-white p-6 shadow-xl max-h-[90vh] overflow-y-auto">
        <h3 className="mb-4 text-lg font-semibold text-slate-900">Nuevo pedido</h3>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-slate-700">
              Cliente <span className="text-red-500">*</span>
            </label>
            <select
              value={clienteId}
              onChange={(e) => setClienteId(e.target.value)}
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
            >
              <option value="">Seleccionar cliente</option>
              {clientes.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.nombre}
                </option>
              ))}
            </select>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-slate-700">Usuario</label>
              <input
                type="text"
                value={usuario}
                onChange={(e) => setUsuario(e.target.value)}
                className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700">Observaciones</label>
              <input
                type="text"
                value={observaciones}
                onChange={(e) => setObservaciones(e.target.value)}
                className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
              />
            </div>
          </div>

          <div>
            <div className="flex items-center justify-between mb-2">
              <label className="block text-sm font-medium text-slate-700">
                Productos <span className="text-red-500">*</span>
              </label>
              <Button variant="secondary" onClick={handleAddLine} type="button">
                + Agregar producto
              </Button>
            </div>

            {lineItems.length === 0 && (
              <p className="text-sm text-slate-400 py-2">No hay productos agregados.</p>
            )}

            {lineItems.map((item, i) => (
              <div key={i} className="mb-2 flex items-end gap-2">
                <div className="flex-1">
                  <label className="block text-xs font-medium text-slate-500 mb-1">Producto</label>
                  <select
                    value={item.productoId}
                    onChange={(e) => handleLineProductoChange(i, Number(e.target.value))}
                    className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
                  >
                    <option value={0}>Seleccionar</option>
                    {productos.map((p) => (
                      <option key={p.id} value={p.id!}>
                        {p.nombre} (${Number(p.precio).toLocaleString("es-AR")})
                      </option>
                    ))}
                  </select>
                </div>
                <div className="w-24">
                  <label className="block text-xs font-medium text-slate-500 mb-1">Cantidad</label>
                  <input
                    type="number"
                    min={1}
                    value={item.cantidad}
                    onChange={(e) => handleLineCantidadChange(i, Number(e.target.value))}
                    className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
                  />
                </div>
                <div className="w-28 text-right text-sm text-slate-600 py-2">
                  {item.precio > 0
                    ? `$${(item.precio * item.cantidad).toLocaleString("es-AR")}`
                    : "—"}
                </div>
                <button
                  type="button"
                  onClick={() => handleRemoveLine(i)}
                  className="text-red-500 hover:text-red-700 text-lg leading-none pb-1"
                >
                  &times;
                </button>
              </div>
            ))}
          </div>

          <div className="text-right text-base font-semibold text-slate-900">
            Total: ${total.toLocaleString("es-AR", { minimumFractionDigits: 2 })}
          </div>

          {error && <p className="text-sm text-red-600">{error}</p>}

          <div className="flex justify-end gap-2 pt-2">
            <Button variant="secondary" onClick={onCancel} disabled={submitting}>
              Cancelar
            </Button>
            <Button type="submit" disabled={submitting}>
              {submitting ? "Creando…" : "Crear pedido"}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
