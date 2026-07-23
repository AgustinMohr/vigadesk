"use client";

import { useEffect, useMemo, useState } from "react";
import { clienteService, type Cliente } from "@/services/clienteService";
import Button from "@/components/Button";
import ClienteTable from "@/components/ClienteTable";
import ClienteForm from "@/components/ClienteForm";
import ClienteDetailModal from "@/components/ClienteDetailModal";
import AsignarObraModal from "@/components/AsignarObraModal";

// Modos de búsqueda disponibles: por nombre, CUIT o email
type SearchMode = "nombre" | "cuit" | "email";

export default function ClientesPageClient({
  initialClientes,
}: {
  initialClientes: Cliente[];
}) {
  // Estados principales
  const [clientes, setClientes] = useState<Cliente[]>(initialClientes);
  const [loading, setLoading] = useState(initialClientes.length === 0);
  const [fetchError, setFetchError] = useState("");

  // Si SSR no trajo datos, el cliente intenta obtenerlos con reintentos cada 5s
  useEffect(() => {
    if (initialClientes.length > 0) return;

    let cancelled = false;

    const fetchData = async () => {
      try {
        const data = await clienteService.getAll();
        if (!cancelled) {
          setClientes(data);
          setLoading(false);
        }
      } catch {
        if (!cancelled) setTimeout(fetchData, 5000);
      }
    };

    fetchData();
    return () => { cancelled = true; };
  }, []);

  // Estados para modales y búsqueda
  const [showForm, setShowForm] = useState(false);
  const [selectedCliente, setSelectedCliente] = useState<Cliente | undefined>();
  const [clienteDetail, setClienteDetail] = useState<Cliente | null>(null);
  const [clienteAssign, setClienteAssign] = useState<Cliente | null>(null);
  const [searchTerm, setSearchTerm] = useState("");
  const [searchMode, setSearchMode] = useState<SearchMode>("nombre");

  // Filtro clientes por nombre/CUIT/email según el modo de búsqueda
  const filteredClientes = useMemo(() => {
    if (!searchTerm.trim()) return clientes;
    const term = searchTerm.toLowerCase().trim();
    return clientes.filter((c) => {
      switch (searchMode) {
        case "nombre":
          return c.nombre.toLowerCase().includes(term);
        case "cuit":
          return (c.cuit ?? "").toLowerCase().includes(term);
        case "email":
          return c.correoElectronico.toLowerCase().includes(term);
        default:
          return true;
      }
    });
  }, [clientes, searchTerm, searchMode]);

  // Handlers: abren modales según la acción
  const handleCreate = () => {
    setSelectedCliente(undefined);
    setShowForm(true);
  };

  const handleEdit = (cliente: Cliente) => {
    setSelectedCliente(cliente);
    setShowForm(true);
  };

  const handleView = (cliente: Cliente) => {
    setClienteDetail(cliente);
  };

  const handleAssignObra = (cliente: Cliente) => {
    setClienteAssign(cliente);
  };

  // Refresca la lista completa desde la API
  const refreshClientes = async () => {
    try {
      const data = await clienteService.getAll();
      setClientes(data);
    } catch {
      setFetchError("No se pudieron cargar los clientes.");
    }
  };

  // Elimina un cliente previa confirmación, luego refresca la lista
  const handleDelete = async (cliente: Cliente) => {
    if (!window.confirm(`¿Eliminar al cliente "${cliente.nombre}"?`)) return;
    try {
      await clienteService.delete(cliente.id!);
      await refreshClientes();
    } catch {
      alert("No se pudo eliminar el cliente.");
    }
  };

  // Crea o edita según si hay un cliente seleccionado, luego refresca
  const handleFormSubmit = async (data: Partial<Cliente>) => {
    if (selectedCliente) {
      await clienteService.update(selectedCliente.id!, data);
    } else {
      await clienteService.create(data);
    }
    setShowForm(false);
    setSelectedCliente(undefined);
    await refreshClientes();
  };

  const handleFormCancel = () => {
    setShowForm(false);
    setSelectedCliente(undefined);
  };

  // Configuración de los modos de búsqueda
  const searchModes: { key: SearchMode; label: string }[] = [
    { key: "nombre", label: "Nombre" },
    { key: "cuit", label: "CUIT" },
    { key: "email", label: "Email" },
  ];

  return (
    <section>
      {/* Encabezado: título y botón para crear cliente */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-semibold">Clientes</h2>
          <p className="mt-2 text-slate-600">
            Gestión de clientes registrados.
          </p>
        </div>
        <Button onClick={handleCreate}>Nuevo cliente</Button>
      </div>

      <div className="mt-6">
        {/* Buscador con selector de modo (nombre / CUIT / email) */}
        <div className="mb-4 flex items-center gap-3">
          <div className="relative flex-1">
            <input
              type="text"
              placeholder={`Buscar por ${searchMode}…`}
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2 pr-8 text-sm focus:border-slate-500 focus:outline-none"
            />
            {/* Botón "X" para limpiar la búsqueda */}
            {searchTerm && (
              <button
                onClick={() => setSearchTerm("")}
                className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 text-sm"
              >
                &times;
              </button>
            )}
          </div>
          {/* Toggle de modo búsqueda */}
          <div className="flex rounded-md border border-slate-300 overflow-hidden">
            {searchModes.map((m) => (
              <button
                key={m.key}
                onClick={() => setSearchMode(m.key)}
                className={`px-3 py-2 text-sm font-medium transition ${
                  searchMode === m.key
                    ? "bg-slate-900 text-white"
                    : "bg-white text-slate-600 hover:bg-slate-100"
                }`}
              >
                {m.label}
              </button>
            ))}
          </div>
        </div>

        {/* Tres estados: cargando, error, o tabla */}
        {loading ? (
          <p className="text-slate-400">Cargando clientes…</p>
        ) : fetchError ? (
          <p className="text-red-600">{fetchError}</p>
        ) : (
          <ClienteTable
            clientes={filteredClientes}
            onEdit={handleEdit}
            onDelete={handleDelete}
            onView={handleView}
            onAssignObra={handleAssignObra}
          />
        )}
      </div>

      {/* Modales que se muestran condicionalmente según la acción */}
      {showForm && (
        <ClienteForm
          cliente={selectedCliente}
          onSubmit={handleFormSubmit}
          onCancel={handleFormCancel}
        />
      )}

      {clienteDetail && (
        <ClienteDetailModal
          cliente={clienteDetail}
          onClose={() => setClienteDetail(null)}
        />
      )}

      {clienteAssign && (
        <AsignarObraModal
          cliente={clienteAssign}
          onAssign={refreshClientes}
          onClose={() => setClienteAssign(null)}
        />
      )}
    </section>
  );
}
