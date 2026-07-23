"use client";

import { useEffect, useState } from "react";
import type { Cliente } from "@/services/clienteService";
import type { Obra } from "@/services/obraService";
import { obraService } from "@/services/obraService";
import Button from "./Button";

// Modal que carga obras sin asignar y permite asociar una a un cliente
type AsignarObraModalProps = {
  cliente: Cliente;
  onAssign: () => void;
  onClose: () => void;
};

export default function AsignarObraModal({
  cliente,
  onAssign,
  onClose,
}: AsignarObraModalProps) {
  const [obras, setObras] = useState<Obra[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedObraId, setSelectedObraId] = useState<number | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  // Al abrir, obtiene obras sin asignar
  useEffect(() => {
    (async () => {
      try {
        const data = await obraService.getSinAsignar();
        setObras(data);
      } catch {
        setError("No se pudieron cargar las obras disponibles.");
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const handleAssign = async () => {
    if (selectedObraId == null) return;
    setSubmitting(true);
    setError("");
    try {
      await obraService.asignarCliente(selectedObraId, cliente.id!);
      onAssign();
      onClose();
    } catch {
      setError("No se pudo asignar la obra.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="w-full max-w-md rounded-lg bg-white p-6 shadow-xl">
        <div className="mb-4 flex items-center justify-between">
          <h3 className="text-lg font-semibold text-slate-900">
            Asignar obra a {cliente.nombre}
          </h3>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-slate-600 text-xl leading-none"
          >
            &times;
          </button>
        </div>

        {loading ? (
          <p className="text-sm text-slate-400">Cargando obras disponibles…</p>
        ) : error && obras.length === 0 ? (
          <p className="text-sm text-red-600">{error}</p>
        ) : obras.length === 0 ? (
          <p className="text-sm text-slate-400">
            No hay obras disponibles para asignar.
          </p>
        ) : (
          // Lista de obras con radio button para seleccionar una
          <div className="space-y-2">
            {obras.map((obra) => (
              <label
                key={obra.id}
                className={`flex cursor-pointer items-center gap-3 rounded-md border p-3 text-sm transition ${
                  selectedObraId === obra.id
                    ? "border-slate-900 bg-slate-50"
                    : "border-slate-200 hover:bg-slate-50"
                }`}
              >
                <input
                  type="radio"
                  name="obra"
                  value={obra.id}
                  checked={selectedObraId === obra.id}
                  onChange={() => setSelectedObraId(obra.id ?? null)}
                  className="accent-slate-900"
                />
                <div>
                  <p className="font-medium text-slate-900">{obra.direccion}</p>
                  <p className="text-slate-500">
                    Presupuesto: ${obra.presupuesto.toLocaleString("es-AR")}
                  </p>
                </div>
              </label>
            ))}
          </div>
        )}

        {error && obras.length > 0 && (
          <p className="mt-2 text-sm text-red-600">{error}</p>
        )}

        <div className="mt-6 flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={submitting}>
            Cancelar
          </Button>
          <Button
            onClick={handleAssign}
            disabled={selectedObraId == null || submitting}
          >
            {submitting ? "Asignando…" : "Asignar"}
          </Button>
        </div>
      </div>
    </div>
  );
}
