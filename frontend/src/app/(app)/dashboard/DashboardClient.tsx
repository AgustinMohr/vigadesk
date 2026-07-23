"use client";

import { useEffect, useState } from "react";
import { clienteService, type Cliente } from "@/services/clienteService";
import { obraService, type Obra } from "@/services/obraService";
import {
  productoService,
  type Producto,
} from "@/services/productoService";
import { pedidoService, type Pedido } from "@/services/pedidoService";

const estadoColors: Record<string, string> = {
  RECIBIDO: "text-yellow-700 bg-yellow-100",
  ACEPTADO: "text-blue-700 bg-blue-100",
  EN_PREPARACION: "text-indigo-700 bg-indigo-100",
  ENTREGADO: "text-green-700 bg-green-100",
  CANCELADO: "text-red-700 bg-red-100",
  RECHAZADO: "text-gray-700 bg-gray-100",
};

function formatCurrency(n: number) {
  return `$${n.toLocaleString("es-AR", { minimumFractionDigits: 2 })}`;
}

export default function DashboardClient() {
  const [clientes, setClientes] = useState<Cliente[]>([]);
  const [obras, setObras] = useState<Obra[]>([]);
  const [productos, setProductos] = useState<Producto[]>([]);
  const [pedidos, setPedidos] = useState<Pedido[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    const fetchAll = async () => {
      try {
        const [c, o, p, pe] = await Promise.all([
          clienteService.getAll(),
          obraService.getAll(),
          productoService.getAll(),
          pedidoService.getAll(),
        ]);
        if (!cancelled) {
          setClientes(c);
          setObras(o);
          setProductos(p);
          setPedidos(pe);
          setLoading(false);
        }
      } catch {
        if (!cancelled) setTimeout(fetchAll, 5000);
      }
    };
    fetchAll();
    return () => { cancelled = true; };
  }, []);

  const estadosPedido = ["RECIBIDO", "ACEPTADO", "EN_PREPARACION", "ENTREGADO", "CANCELADO"];
  const pedidosPorEstado = Object.fromEntries(
    estadosPedido.map((est) => [est, pedidos.filter((p) => p.estado === est).length]),
  );
  const totalPedidos = pedidos.reduce((sum, p) => sum + (p.total ?? 0), 0);
  const productosStockBajo = productos.filter((p) => p.stockActual < p.stockMinimo);

  if (loading) {
    return (
      <section>
        <h2 className="text-2xl font-semibold">Dashboard</h2>
        <p className="mt-2 text-slate-600">Resumen general del sistema.</p>
        <p className="mt-8 text-slate-400">Cargando información…</p>
      </section>
    );
  }

  return (
    <section>
      <h2 className="text-2xl font-semibold">Dashboard</h2>
      <p className="mt-2 text-slate-600">Resumen general del sistema.</p>

      <div className="mt-6 grid grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <p className="text-sm font-medium text-slate-500">Clientes</p>
          <p className="mt-1 text-3xl font-semibold text-slate-900">
            {clientes.length}
          </p>
        </div>
        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <p className="text-sm font-medium text-slate-500">Obras</p>
          <p className="mt-1 text-3xl font-semibold text-slate-900">
            {obras.length}
          </p>
        </div>
        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <p className="text-sm font-medium text-slate-500">Productos</p>
          <p className="mt-1 text-3xl font-semibold text-slate-900">
            {productos.length}
          </p>
        </div>
        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <p className="text-sm font-medium text-slate-500">Pedidos</p>
          <p className="mt-1 text-3xl font-semibold text-slate-900">
            {pedidos.length}
          </p>
        </div>
      </div>

      <div className="mt-6 grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h3 className="text-sm font-semibold text-slate-700">
            Pedidos por estado
          </h3>
          <div className="mt-3 space-y-2">
            {estadosPedido.map((est) => (
              <div
                key={est}
                className="flex items-center justify-between text-sm"
              >
                <span
                  className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${
                    estadoColors[est] ?? "text-slate-700 bg-slate-100"
                  }`}
                >
                  {est}
                </span>
                <span className="font-medium text-slate-900">
                  {pedidosPorEstado[est]}
                </span>
              </div>
            ))}
          </div>
          <hr className="my-3 border-slate-200" />
          <div className="flex items-center justify-between text-sm">
            <span className="font-medium text-slate-500">Total acumulado</span>
            <span className="text-lg font-semibold text-slate-900">
              {formatCurrency(totalPedidos)}
            </span>
          </div>
        </div>

        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h3 className="text-sm font-semibold text-slate-700">
            Productos con stock bajo
          </h3>
          {productosStockBajo.length === 0 ? (
            <p className="mt-3 text-sm text-slate-400">
              Todos los productos tienen stock suficiente.
            </p>
          ) : (
            <div className="mt-3 space-y-2">
              {productosStockBajo.slice(0, 10).map((p) => (
                <div
                  key={p.id}
                  className="flex items-center justify-between rounded-md bg-slate-50 px-3 py-2 text-sm"
                >
                  <span className="text-slate-900">{p.nombre}</span>
                  <span className="text-red-600 font-medium">
                    {p.stockActual} / {p.stockMinimo}
                  </span>
                </div>
              ))}
              {productosStockBajo.length > 10 && (
                <p className="text-xs text-slate-400 text-center pt-1">
                  y {productosStockBajo.length - 10} más…
                </p>
              )}
            </div>
          )}
        </div>
      </div>
    </section>
  );
}
