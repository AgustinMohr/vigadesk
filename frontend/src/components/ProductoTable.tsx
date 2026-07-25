"use client";

import type { Producto } from "@/services/productoService";
import Button from "./Button";
import { useSort } from "@/hooks/useSort";
import { useAuth } from "@/lib/auth/AuthContext";

const thClass =
  "px-4 py-3 text-left font-medium text-slate-600 cursor-pointer select-none hover:text-slate-900";
const thClassRight =
  "px-4 py-3 text-right font-medium text-slate-600 cursor-pointer select-none hover:text-slate-900";

type ProductoTableProps = {
  productos: Producto[];
  onEdit: (producto: Producto) => void;
  onDelete: (producto: Producto) => void;
  onAplicarDescuento: (producto: Producto) => void;
};

export default function ProductoTable({
  productos,
  onEdit,
  onDelete,
  onAplicarDescuento,
}: ProductoTableProps) {
  const { isAdmin } = useAuth();
  const { sortedData, toggleSort, indicator } = useSort(productos, "nombre");

  return (
    <div className="overflow-x-auto rounded-lg border border-slate-200">
      <table className="min-w-full divide-y divide-slate-200 text-sm">
        <thead className="bg-slate-50">
          <tr>
            <th className={thClass} onClick={() => toggleSort("codigo")}>
              Código{indicator("codigo")}
            </th>
            <th className={thClass} onClick={() => toggleSort("nombre")}>
              Nombre{indicator("nombre")}
            </th>
            <th className={thClass} onClick={() => toggleSort("precio")}>
              Precio{indicator("precio")}
            </th>
            <th className={thClass} onClick={() => toggleSort("stockActual")}>
              Stock Actual{indicator("stockActual")}
            </th>
            <th className={thClass} onClick={() => toggleSort("stockMinimo")}>
              Stock Mínimo{indicator("stockMinimo")}
            </th>
            <th className={thClass} onClick={() => toggleSort("categoria")}>
              Categoría{indicator("categoria")}
            </th>
            <th className={thClassRight} onClick={() => toggleSort("descuentoPromocional")}>
              Desc. Promocional{indicator("descuentoPromocional")}
            </th>
            <th className="px-4 py-3 text-right font-medium text-slate-600">
              Acciones
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-200">
          {sortedData.length === 0 ? (
            <tr>
              <td colSpan={8} className="px-4 py-8 text-center text-slate-400">
                No hay productos registrados.
              </td>
            </tr>
          ) : (
            sortedData.map((producto) => (
              <tr key={producto.id} className="hover:bg-slate-50">
                <td className="px-4 py-3 font-medium text-slate-900">
                  {producto.codigo ?? "—"}
                </td>
                <td className="px-4 py-3 text-slate-600">{producto.nombre}</td>
                <td className="px-4 py-3 text-slate-600">
                  ${Number(producto.precio).toLocaleString("es-AR", { minimumFractionDigits: 2 })}
                </td>
                <td className="px-4 py-3 text-slate-600">{producto.stockActual}</td>
                <td className="px-4 py-3 text-slate-600">{producto.stockMinimo}</td>
                <td className="px-4 py-3 text-slate-600">{producto.categoria ?? "—"}</td>
                <td className="px-4 py-3 text-right text-slate-600">
                  {producto.descuentoPromocional && producto.descuentoPromocional > 0
                    ? `$${Number(producto.descuentoPromocional).toLocaleString("es-AR", { minimumFractionDigits: 2 })}`
                    : "—"}
                </td>
                <td className="px-4 py-3 text-right">
                  <div className="flex justify-end gap-2">
                    {isAdmin && (
                      <Button variant="secondary" onClick={() => onAplicarDescuento(producto)}>
                        Descuento
                      </Button>
                    )}
                    {isAdmin && (
                      <Button variant="secondary" onClick={() => onEdit(producto)}>
                        Editar
                      </Button>
                    )}
                    {isAdmin && (
                      <Button variant="danger" onClick={() => onDelete(producto)}>
                        Eliminar
                      </Button>
                    )}
                  </div>
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}
