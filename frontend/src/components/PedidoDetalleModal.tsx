"use client";

import type { Pedido } from "@/services/pedidoService";
import Button from "./Button";

type PedidoDetalleModalProps = {
  pedido: Pedido;
  onClose: () => void;
};

const estadoColors: Record<string, string> = {
  RECIBIDO: "text-yellow-700 bg-yellow-100",
  ACEPTADO: "text-blue-700 bg-blue-100",
  EN_PREPARACION: "text-indigo-700 bg-indigo-100",
  ENTREGADO: "text-green-700 bg-green-100",
  CANCELADO: "text-red-700 bg-red-100",
  RECHAZADO: "text-gray-700 bg-gray-100",
};

function formatDate(iso?: string) {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString("es-AR", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function formatCurrency(n?: number) {
  if (n == null) return "—";
  return `$${n.toLocaleString("es-AR", { minimumFractionDigits: 2 })}`;
}

export default function PedidoDetalleModal({
  pedido,
  onClose,
}: PedidoDetalleModalProps) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="w-full max-w-2xl rounded-lg bg-white p-6 shadow-xl">
        <div className="mb-4 flex items-center justify-between">
          <h3 className="text-lg font-semibold text-slate-900">
            Detalle del pedido #{pedido.numeroPedido}
          </h3>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-slate-600 text-xl leading-none"
          >
            &times;
          </button>
        </div>

        <div className="grid grid-cols-2 gap-4 text-sm">
          <div>
            <span className="font-medium text-slate-500">Cliente:</span>
            <p className="text-slate-900">{pedido.cliente.nombre}</p>
          </div>
          <div>
            <span className="font-medium text-slate-500">Obra:</span>
            <p className="text-slate-900">{pedido.obra?.direccion ?? "—"}</p>
          </div>
          <div>
            <span className="font-medium text-slate-500">Fecha:</span>
            <p className="text-slate-900">{formatDate(pedido.fecha)}</p>
          </div>
          <div>
            <span className="font-medium text-slate-500">Estado:</span>
            <span
              className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium mt-0.5 ${
                estadoColors[pedido.estado] ?? "text-slate-700 bg-slate-100"
              }`}
            >
              {pedido.estado}
            </span>
          </div>
          <div className="col-span-2">
            <span className="font-medium text-slate-500">Observaciones:</span>
            <p className="text-slate-900">{pedido.observaciones ?? "—"}</p>
          </div>
          <div className="col-span-2">
            <span className="font-medium text-slate-500">Total:</span>
            <p className="text-lg font-semibold text-slate-900">
              {formatCurrency(pedido.total)}
            </p>
          </div>
        </div>

        <hr className="my-4 border-slate-200" />

        <h4 className="mb-3 text-sm font-semibold text-slate-700">
          Detalle del pedido
        </h4>

        <div className="overflow-x-auto rounded-lg border border-slate-200">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50">
              <tr>
                <th className="px-3 py-2 text-left font-medium text-slate-600">Producto</th>
                <th className="px-3 py-2 text-right font-medium text-slate-600">Cantidad</th>
                <th className="px-3 py-2 text-right font-medium text-slate-600">P. Unitario</th>
                <th className="px-3 py-2 text-right font-medium text-slate-600">Descuento</th>
                <th className="px-3 py-2 text-right font-medium text-slate-600">Subtotal</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200">
              {pedido.detalle.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-3 py-6 text-center text-slate-400">
                    El pedido no tiene productos.
                  </td>
                </tr>
              ) : (
                pedido.detalle.map((item, idx) => (
                  <tr key={idx} className="hover:bg-slate-50">
                    <td className="px-3 py-2 text-slate-900">
                      {item.producto.nombre ?? `Producto #${item.producto.id}`}
                    </td>
                    <td className="px-3 py-2 text-right text-slate-600">{item.cantidad}</td>
                    <td className="px-3 py-2 text-right text-slate-600">
                      {formatCurrency(item.precioUnitario ?? item.producto.precio ?? 0)}
                    </td>
                    <td className="px-3 py-2 text-right text-slate-600">
                      {item.descuento && item.descuento > 0
                        ? formatCurrency(item.descuento)
                        : "—"}
                    </td>
                    <td className="px-3 py-2 text-right font-medium text-slate-900">
                      {formatCurrency((item.precioUnitario ?? item.producto.precio ?? 0) * item.cantidad)}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        <div className="mt-6 flex justify-end">
          <Button variant="secondary" onClick={onClose}>
            Cerrar
          </Button>
        </div>
      </div>
    </div>
  );
}
