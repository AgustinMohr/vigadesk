// ==============================================================
// COMPONENTE CLIENTE de /productos (el "cerebro" de la pantalla)
// --------------------------------------------------------------
// Este archivo concentra TODA la lógica interactiva de la página de
// productos: traerlos de la API, filtrarlos por múltiples criterios
// (nombre, código, precio y stock en rangos min/max), y abrir modales
// para crear/editar, aplicar un descuento promocional o REPONER stock.
// Por eso es un Client Component ("use client").
//
// RECORRIDO DE UNA PANTALLA CON DATOS (patrón compartido con obras,
// clientes, pedidos y usuarios):
//  1. El Server Component (page.tsx) renderiza este cliente con datos
//     iniciales (`initialProductos`).
//  2. `useFetch` pide los datos a la API al montar (error con "Reintentar").
//  3. El estado (`productos`) guarda la data; al cambiar, React re-renderiza.
//  4. `useMemo` recalcula el filtro SOLO cuando cambian sus dependencias.
//  5. Los modales (ProductoForm, DescuentoModal) se muestran según
//     variables de estado booleanas/nulas.
//
// PARTICULARIDAD: esta pantalla usa 6 filtros de búsqueda combinables
// (a diferencia de obras/clientes que filtran por un único texto).
// Además incluye el flujo de descuento promocional vía DescuentoModal y
// la reposición de stock vía ProvisionModal (solo admin).
//
// CONCEPTOS CLAVE: Client Component, useState, useEffect con retries,
// useMemo para filtrado derivado, handlers como props, render
// condicional, non-null assertion (`id!`), optional chaining (`?.`),
// contexto de autenticación (useAuth).
// ==============================================================

'use client';

import { useMemo, useState } from 'react';
import { productoService, type Producto } from '@/services/productoService';
import Button from '@/components/Button';
import ProductoTable from '@/components/ProductoTable';
import ProductoForm from '@/components/ProductoForm';
import DescuentoModal from '@/components/DescuentoModal';
import ProvisionModal from '@/components/ProvisionModal';
import { useAuth } from '@/lib/auth/AuthContext';
import { useFetch } from '@/hooks/useFetch';
import FetchError from '@/components/FetchError';
import { getErrorMessage } from '@/lib/http/api-error';

/**
 * Componente "cerebro" de la página /productos: maneja el estado de los
 * productos, la carga inicial (con error y "Reintentar"), los 6 filtros de búsqueda
 * (nombre, código, precio min/max, stock min/max) y los modales de
 * crear/editar y de aplicar descuento promocional.
 *
 * @param {{ initialProductos: Producto[] }} props - Datos iniciales que
 *   manda el Server Component (page.tsx). Si vienen vacíos, este cliente
 *   hace el fetch real a la API.
 * @returns {JSX.Element} La sección completa con filtros, tabla y modales.
 */
export default function ProductosPageClient () {
  // --------------------------------------------------------------
  // useState: estado local del componente.
  //  - `productos`: la lista que se muestra en la tabla.
  //  - `loading`: mientras sea true se muestra "Cargando…".
  //  - `fetchError`: mensaje si falla la carga inicial.
  // Si SSR ya hidrató los productos, `loading` arranca en false y no
  // se muestra ningún estado de espera.
  // --------------------------------------------------------------
  // Carga al montar y expone carga y error; no reintenta solo (el usuario elige "Reintentar").
  const { data: productos, loading, error: fetchError, retryable, reload } = useFetch(
    () => productoService.getAll(),
    [] as Producto[],
    'No se pudieron cargar los productos.'
  );

  // --------------------------------------------------------------
  // Estados para el modal de formulario, el modal de descuento y los
  // 6 campos de búsqueda. Cada campo del filtro es una pieza de estado
  // independiente para poder combinarlos entre sí.
  // --------------------------------------------------------------
  const [showForm, setShowForm] = useState(false);
  const [selectedProducto, setSelectedProducto] = useState<Producto | undefined>();
  // `productoDescuento` guarda el producto sobre el que se abrirá el
  // DescuentoModal. Es `null` → modal cerrado.
  const [productoDescuento, setProductoDescuento] = useState<Producto | null>(null);
  // `productoProvision` guarda el producto sobre el que se abrirá el
  // ProvisionModal (reposición de stock). `null` → modal cerrado.
  const [productoProvision, setProductoProvision] = useState<Producto | null>(null);
  const [searchNombre, setSearchNombre] = useState('');
  const [searchCodigo, setSearchCodigo] = useState('');
  const [searchPrecioMin, setSearchPrecioMin] = useState('');
  const [searchPrecioMax, setSearchPrecioMax] = useState('');
  const [searchStockMin, setSearchStockMin] = useState('');
  const [searchStockMax, setSearchStockMax] = useState('');

  // --------------------------------------------------------------
  // useMemo: filtra los productos aplicando SOLO los filtros que el
  // usuario completó (si el campo está vacío, no restringe).
  // Se recalcula únicamente cuando cambian las dependencias. `Number(...)`
  // convierte el string del input en número para poder comparar rangos.
  // --------------------------------------------------------------
  const filteredProductos = useMemo(() => {
    return productos.filter((p) => {
      // Filtro por nombre (insensible a mayúsculas).
      if (searchNombre && !p.nombre.toLowerCase().includes(searchNombre.toLowerCase())) return false;
      // Filtro por código; `?? ''` tolera que `codigo` sea opcional/null.
      if (searchCodigo && !(p.codigo ?? '').toLowerCase().includes(searchCodigo.toLowerCase())) return false;
      // Rango de precio mínimo: descarta los que tienen precio menor.
      if (searchPrecioMin && (p.precio ?? 0) < Number(searchPrecioMin)) return false;
      // Rango de precio máximo: descarta los que superan el tope.
      if (searchPrecioMax && (p.precio ?? 0) > Number(searchPrecioMax)) return false;
      // Rango de stock mínimo.
      if (searchStockMin && (p.stockActual ?? 0) < Number(searchStockMin)) return false;
      // Rango de stock máximo.
      if (searchStockMax && (p.stockActual ?? 0) > Number(searchStockMax)) return false;
      // Pasó todos los filtros → se conserva.
      return true;
    });
  }, [productos, searchNombre, searchCodigo, searchPrecioMin, searchPrecioMax, searchStockMin, searchStockMax]);

  // useAuth: lee el rol del usuario desde el Context global. `isAdmin`
  // decide si mostramos el botón "Nuevo producto".
  const { isAdmin } = useAuth();

  // --------------------------------------------------------------
  // HANDLERS: funciones que responden a eventos del usuario.
  // --------------------------------------------------------------

  // Abre el form en modo "crear".
  const handleCreate = () => {
    setSelectedProducto(undefined);
    setShowForm(true);
  };

  // Abre el form en modo "editar".
  const handleEdit = (producto: Producto) => {
    setSelectedProducto(producto);
    setShowForm(true);
  };

  // Vuelve a pedir TODOS los productos a la API para reflejar cambios.
  const refreshProductos = () => reload({ silent: true });

  // Borra un producto previa confirmación, luego refresca la lista.
  const handleDelete = async (producto: Producto) => {
    if (!window.confirm(`¿Eliminar producto "${producto.nombre}"?`)) return;
    try {
      await productoService.delete(producto.id!);
      await refreshProductos();
    } catch (err) {
      alert(getErrorMessage(err, 'No se pudo eliminar el producto.'));
    }
  };

  // Crea o actualiza según si hay un producto seleccionado.
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

  // Cancela el formulario sin guardar.
  const handleFormCancel = () => {
    setShowForm(false);
    setSelectedProducto(undefined);
  };

  // Abre el DescuentoModal guardando el producto elegido en estado.
  const handleAplicarDescuento = (producto: Producto) => {
    setProductoDescuento(producto);
  };

  // Envía el nuevo descuento promocional al backend, cierra el modal y
  // refresca la lista para ver el precio actualizado.
  const handleDescuentoSubmit = async (id: number, descuentoPromocional: number) => {
    await productoService.aplicarDescuento(id, descuentoPromocional);
    setProductoDescuento(null);
    await refreshProductos();
  };

  // Cierra el DescuentoModal sin aplicar cambios.
  const handleDescuentoCancel = () => {
    setProductoDescuento(null);
  };

  // Abre el ProvisionModal guardando el producto elegido en estado.
  const handleReponerStock = (producto: Producto) => {
    setProductoProvision(producto);
  };

  // Envía la cantidad a reponer al backend, cierra el modal y refresca la
  // lista para ver el stock actualizado (el precio no se modifica).
  const handleProvisionSubmit = async (id: number, cantidad: number) => {
    await productoService.provision(id, cantidad);
    setProductoProvision(null);
    await refreshProductos();
  };

  // Cierra el ProvisionModal sin reponer.
  const handleProvisionCancel = () => {
    setProductoProvision(null);
  };

  return (
    <section>
      {/* Encabezado: título + botón de crear (solo admin) */}
      <div className='flex items-center justify-between'>
        <div>
          <h2 className='text-2xl font-semibold'>Productos</h2>
          <p className='mt-2 text-slate-600'>
            Gestión del catálogo de productos.
          </p>
        </div>
        {isAdmin && <Button onClick={handleCreate}>Nuevo producto</Button>}
      </div>

      {/* Grilla de 6 inputs para los filtros. Cada uno alimenta su pieza
          de estado; el filtrado combinado ocurre en `filteredProductos`. */}
      <div className='mt-6'>
        <div className='mb-4 grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3'>
          <input placeholder='Nombre…' value={searchNombre} onChange={(e) => setSearchNombre(e.target.value)} className='rounded border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none' />
          <input placeholder='Código…' value={searchCodigo} onChange={(e) => setSearchCodigo(e.target.value)} className='rounded border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none' />
          <input type='number' placeholder='Precio mín…' value={searchPrecioMin} onChange={(e) => setSearchPrecioMin(e.target.value)} className='rounded border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none' />
          <input type='number' placeholder='Precio máx…' value={searchPrecioMax} onChange={(e) => setSearchPrecioMax(e.target.value)} className='rounded border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none' />
          <input type='number' placeholder='Stock mín…' value={searchStockMin} onChange={(e) => setSearchStockMin(e.target.value)} className='rounded border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none' />
          <input type='number' placeholder='Stock máx…' value={searchStockMax} onChange={(e) => setSearchStockMax(e.target.value)} className='rounded border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none' />
        </div>

        {/* Render condicional con 3 estados: cargando / error / tabla. */}
        {loading
          ? (
            <p className='text-slate-400'>Cargando productos…</p>
            )
          : fetchError
            ? (
              <FetchError mensaje={fetchError} onRetry={retryable ? () => reload() : undefined} />
              )
            : (
              <ProductoTable
                productos={filteredProductos}
                onEdit={handleEdit}
                onDelete={handleDelete}
                onAplicarDescuento={handleAplicarDescuento}
                onReponerStock={handleReponerStock}
              />
              )}
      </div>

      {/* Modales: se montan solo si su variable es truthy. */}
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

      {productoProvision && (
        <ProvisionModal
          producto={productoProvision}
          onSubmit={handleProvisionSubmit}
          onCancel={handleProvisionCancel}
        />
      )}
    </section>
  );
}
