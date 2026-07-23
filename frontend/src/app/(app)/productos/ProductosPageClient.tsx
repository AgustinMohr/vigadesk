"use client";

import { useEffect, useMemo, useState } from "react";
import { productoService, type Producto } from "@/services/productoService";
import Button from "@/components/Button";
import ProductoTable from "@/components/ProductoTable";
import ProductoForm from "@/components/ProductoForm";
import DescuentoModal from "@/components/DescuentoModal";

export default function ProductosPageClient({
  initialProductos,
}: {
  initialProductos: Producto[];
}) {
  // Si SSR no trajo datos, reintenta cada 5s hasta obtenerlos
  const [productos, setProductos] = useState<Producto[]>(initialProductos);
  const [loading, setLoading] = useState(initialProductos.length === 0);
  const [fetchError, setFetchError] = useState("");

  useEffect(() => {
    if (initialProductos.length > 0) return;

    let cancelled = false;

    const fetchData = async () => {
      try {
        const data = await productoService.getAll();
        if (!cancelled) {
          setProductos(data);
          setLoading(false);
        }
      } catch {
        if (!cancelled) setTimeout(fetchData, 5000);
      }
    };

    fetchData();
    return () => { cancelled = true; };
  }, []);

  // Estados para modal de formulario, descuento y filtros de búsqueda
  const [showForm, setShowForm] = useState(false);
  const [selectedProducto, setSelectedProducto] = useState<Producto | undefined>();
  const [productoDescuento, setProductoDescuento] = useState<Producto | null>(null);
  const [searchNombre, setSearchNombre] = useState("");
  const [searchCodigo, setSearchCodigo] = useState("");
  const [searchPrecioMin, setSearchPrecioMin] = useState("");
  const [searchPrecioMax, setSearchPrecioMax] = useState("");
  const [searchStockMin, setSearchStockMin] = useState("");
  const [searchStockMax, setSearchStockMax] = useState("");

  // Filtros: aplica SOLO si el usuario escribió algo en el campo correspondiente
  const filteredProductos = useMemo(() => {
    return productos.filter((p) => {
      if (searchNombre && !p.nombre.toLowerCase().includes(searchNombre.toLowerCase())) return false;
      if (searchCodigo && !(p.codigo ?? "").toLowerCase().includes(searchCodigo.toLowerCase())) return false;
      if (searchPrecioMin && (p.precio ?? 0) < Number(searchPrecioMin)) return false;
      if (searchPrecioMax && (p.precio ?? 0) > Number(searchPrecioMax)) return false;
      if (searchStockMin && (p.stockActual ?? 0) < Number(searchStockMin)) return false;
      if (searchStockMax && (p.stockActual ?? 0) > Number(searchStockMax)) return false;
      return true;
    });
  }, [productos, searchNombre, searchCodigo, searchPrecioMin, searchPrecioMax, searchStockMin, searchStockMax]);

  const handleCreate = () => {
    setSelectedProducto(undefined);
    setShowForm(true);
  };

  const handleEdit = (producto: Producto) => {
    setSelectedProducto(producto);
    setShowForm(true);
  };

  // Refresca la lista completa desde la API
  const refreshProductos = async () => {
    try {
      const data = await productoService.getAll();
      setProductos(data);
    } catch {
      setFetchError("No se pudieron cargar los productos.");
    }
  };

  // Elimina producto previa confirmación, luego refresca
  const handleDelete = async (producto: Producto) => {
    if (!window.confirm(`¿Eliminar producto "${producto.nombre}"?`)) return;
    try {
      await productoService.delete(producto.id!);
      await refreshProductos();
    } catch {
      alert("No se pudo eliminar el producto.");
    }
  };

  // Crea o edita según si hay producto seleccionado
  const handleFormSubmit = async (data: Partial<Producto>) => {
    if (selectedProducto) {
      await productoService.update(selectedProducto.id!, data);
    } else {
      await productoService.create(data);
    }
    setShowForm(false);
    setSelectedProducto(undefined);
    await refreshProductos();
  };

  const handleFormCancel = () => {
    setShowForm(false);
    setSelectedProducto(undefined);
  };

  const handleAplicarDescuento = (producto: Producto) => {
    setProductoDescuento(producto);
  };

  const handleDescuentoSubmit = async (id: number, descuentoPromocional: number) => {
    await productoService.aplicarDescuento(id, descuentoPromocional);
    setProductoDescuento(null);
    await refreshProductos();
  };

  const handleDescuentoCancel = () => {
    setProductoDescuento(null);
  };

  return (
    <section>
      {/* Encabezado: título y botón crear */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-semibold">Productos</h2>
          <p className="mt-2 text-slate-600">
            Gestión del catálogo de productos.
          </p>
        </div>
        <Button onClick={handleCreate}>Nuevo producto</Button>
      </div>

      {/* Filtros de búsqueda: nombre, código, precio (min/max), stock (min/max) */}
      <div className="mt-6">
        <div className="mb-4 grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3">
          <input placeholder="Nombre…" value={searchNombre} onChange={(e) => setSearchNombre(e.target.value)} className="rounded border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none" />
          <input placeholder="Código…" value={searchCodigo} onChange={(e) => setSearchCodigo(e.target.value)} className="rounded border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none" />
          <input type="number" placeholder="Precio mín…" value={searchPrecioMin} onChange={(e) => setSearchPrecioMin(e.target.value)} className="rounded border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none" />
          <input type="number" placeholder="Precio máx…" value={searchPrecioMax} onChange={(e) => setSearchPrecioMax(e.target.value)} className="rounded border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none" />
          <input type="number" placeholder="Stock mín…" value={searchStockMin} onChange={(e) => setSearchStockMin(e.target.value)} className="rounded border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none" />
          <input type="number" placeholder="Stock máx…" value={searchStockMax} onChange={(e) => setSearchStockMax(e.target.value)} className="rounded border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none" />
        </div>

        {loading ? (
          <p className="text-slate-400">Cargando productos…</p>
        ) : fetchError ? (
          <p className="text-red-600">{fetchError}</p>
        ) : (
          <ProductoTable
            productos={filteredProductos}
            onEdit={handleEdit}
            onDelete={handleDelete}
            onAplicarDescuento={handleAplicarDescuento}
          />
        )}
      </div>

      {showForm && (
        <ProductoForm
          producto={selectedProducto}
          onSubmit={handleFormSubmit}
          onCancel={handleFormCancel}
        />
      )}

      {productoDescuento && (
        <DescuentoModal
          producto={productoDescuento}
          onSubmit={handleDescuentoSubmit}
          onCancel={handleDescuentoCancel}
        />
      )}
    </section>
  );
}
