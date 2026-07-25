"use client";

import type { Cliente } from "@/services/clienteService";
import Button from "./Button";
import { useSort } from "@/hooks/useSort";
import { useAuth } from "@/lib/auth/AuthContext";

const thClass =
  "px-4 py-3 text-left font-medium text-slate-600 cursor-pointer select-none hover:text-slate-900";

type ClienteTableProps = {
  clientes: Cliente[];
  onEdit: (cliente: Cliente) => void;
  onDelete: (cliente: Cliente) => void;
  onView: (cliente: Cliente) => void;
  onAssignObra: (cliente: Cliente) => void;
};

export default function ClienteTable({
  clientes,
  onEdit,
  onDelete,
  onView,
  onAssignObra,
}: ClienteTableProps) {
  const { isAdmin } = useAuth();
  const { sortedData, toggleSort, indicator } = useSort(clientes, "nombre");

  return (
    <div className="overflow-x-auto rounded-lg border border-slate-200">
      <table className="min-w-full divide-y divide-slate-200 text-sm">
        <thead className="bg-slate-50">
          <tr>
            <th className={thClass} onClick={() => toggleSort("nombre")}>
              Nombre{indicator("nombre")}
            </th>
            <th className={thClass} onClick={() => toggleSort("correoElectronico")}>
              Email{indicator("correoElectronico")}
            </th>
            <th className={thClass} onClick={() => toggleSort("cuit")}>
              CUIT{indicator("cuit")}
            </th>
            <th className={thClass} onClick={() => toggleSort("maximoDescubierto")}>
              Desc. Máximo{indicator("maximoDescubierto")}
            </th>
            <th className="px-4 py-3 text-right font-medium text-slate-600">
              Acciones
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-200">
          {sortedData.length === 0 ? (
            <tr>
              <td colSpan={5} className="px-4 py-8 text-center text-slate-400">
                No hay clientes registrados.
              </td>
            </tr>
          ) : (
            sortedData.map((cliente) => (
              <tr key={cliente.id} className="hover:bg-slate-50">
                <td className="px-4 py-3 font-medium text-slate-900">
                  {cliente.nombre}
                </td>
                <td className="px-4 py-3 text-slate-600">
                  {cliente.correoElectronico}
                </td>
                <td className="px-4 py-3 text-slate-600">
                  {cliente.cuit ?? "—"}
                </td>
                <td className="px-4 py-3 text-slate-600">
                  {cliente.maximoDescubierto != null
                    ? `$${cliente.maximoDescubierto.toLocaleString("es-AR")}`
                    : "—"}
                </td>
                <td className="px-4 py-3 text-right">
                  <div className="flex justify-end gap-2">
                    <Button variant="secondary" onClick={() => onView(cliente)}>
                      Ver
                    </Button>
                    {isAdmin && (
                      <Button variant="secondary" onClick={() => onAssignObra(cliente)}>
                        Asignar obra
                      </Button>
                    )}
                    {isAdmin && (
                      <Button variant="secondary" onClick={() => onEdit(cliente)}>
                        Editar
                      </Button>
                    )}
                    {isAdmin && (
                      <Button variant="danger" onClick={() => onDelete(cliente)}>
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
