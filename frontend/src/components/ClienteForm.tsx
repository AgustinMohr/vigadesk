"use client";

import { useState } from "react";
import type { Cliente } from "@/services/clienteService";
import Button from "./Button";

// Modal formulario para crear o editar un cliente
type ClienteFormProps = {
  cliente?: Cliente;          // Si existe → edición, si no → creación
  onSubmit: (data: Partial<Cliente>) => Promise<void>;
  onCancel: () => void;
};

export default function ClienteForm({
  cliente,
  onSubmit,
  onCancel,
}: ClienteFormProps) {
  const [nombre, setNombre] = useState(cliente?.nombre ?? "");
  const [correoElectronico, setCorreoElectronico] = useState(
    cliente?.correoElectronico ?? ""
  );
  const [cuit, setCuit] = useState(cliente?.cuit ?? "");
  const [maximoDescubierto, setMaximoDescubierto] = useState(
    cliente?.maximoDescubierto?.toString() ?? ""
  );
  const [maxObrasActivas, setMaxObrasActivas] = useState(
    cliente?.maxObrasActivas?.toString() ?? ""
  );
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const isEditing = !!cliente;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");

    if (!nombre.trim() || !correoElectronico.trim()) {
      setError("Nombre y correo electrónico son obligatorios.");
      return;
    }
    if (!maxObrasActivas.trim() || Number(maxObrasActivas) < 1) {
      setError("Las obras activas máximas deben ser al menos 1.");
      return;
    }

    setSubmitting(true);
    try {
      await onSubmit({
        nombre: nombre.trim(),
        correoElectronico: correoElectronico.trim(),
        cuit: cuit.trim() || undefined,
        maximoDescubierto: maximoDescubierto
          ? Number(maximoDescubierto)
          : undefined,
        maxObrasActivas: Number(maxObrasActivas),
      });
    } catch {
      setError("Ocurrió un error al guardar el cliente.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="w-full max-w-md rounded-lg bg-white p-6 shadow-xl">
        <h3 className="mb-4 text-lg font-semibold text-slate-900">
          {isEditing ? "Editar cliente" : "Nuevo cliente"}
        </h3>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-slate-700">
              Nombre <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              value={nombre}
              onChange={(e) => setNombre(e.target.value)}
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-slate-700">
              Correo electrónico <span className="text-red-500">*</span>
            </label>
            <input
              type="email"
              value={correoElectronico}
              onChange={(e) => setCorreoElectronico(e.target.value)}
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-slate-700">
              CUIT
            </label>
            <input
              type="text"
              value={cuit}
              onChange={(e) => setCuit(e.target.value)}
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-slate-700">
              Descubierto máximo
            </label>
            <input
              type="number"
              value={maximoDescubierto}
              onChange={(e) => setMaximoDescubierto(e.target.value)}
              min={10000}
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-slate-700">
              Obras activas máximas <span className="text-red-500">*</span>
            </label>
            <input
              type="number"
              value={maxObrasActivas}
              onChange={(e) => setMaxObrasActivas(e.target.value)}
              min={1}
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
            />
          </div>

          {error && (
            <p className="text-sm text-red-600">{error}</p>
          )}

          <div className="flex justify-end gap-2 pt-2">
            <Button
              variant="secondary"
              onClick={onCancel}
              disabled={submitting}
            >
              Cancelar
            </Button>
            <Button type="submit" disabled={submitting}>
              {submitting ? "Guardando…" : "Guardar"}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
