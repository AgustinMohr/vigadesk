'use client';

import { useState } from 'react';
import Button from '@/components/Button';
import { importService, type ImportResult } from '@/services/importService';
import { getErrorMessage } from '@/lib/http/api-error';
import { useAuth } from '@/lib/auth/AuthContext';

type CardProps = {
  title: string;
  columns: string;
  onImport: (file: File) => Promise<ImportResult>;
};

function ImportCard ({ title, columns, onImport }: CardProps) {
  const [file, setFile] = useState<File | null>(null);
  const [result, setResult] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setResult('');
    setError('');
    if (!file) {
      setError('Seleccioná un archivo CSV.');
      return;
    }
    setLoading(true);
    try {
      const response = await onImport(file);
      setResult(response.mensaje);
    } catch (err) {
      setError(getErrorMessage(err, 'No se pudo importar el CSV.'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <section className='rounded-lg border border-slate-200 bg-white p-5'>
      <h3 className='text-base font-semibold text-slate-900'>{title}</h3>
      <p className='mt-1 text-xs text-slate-500'>
        Columnas: <code>{columns}</code>
      </p>
      <form onSubmit={handleSubmit} className='mt-4 space-y-3'>
        <input
          type='file'
          accept='.csv,text/csv'
          onChange={(e) => setFile(e.target.files?.[0] ?? null)}
          className='block w-full text-sm text-slate-600 file:mr-3 file:rounded file:border-0 file:bg-slate-900 file:px-3 file:py-2 file:text-sm file:text-white'
        />
        {result && <p className='text-sm text-emerald-700'>{result}</p>}
        {error && <p className='text-sm text-red-600'>{error}</p>}
        <Button type='submit' disabled={loading}>
          {loading ? 'Importando…' : 'Importar CSV'}
        </Button>
      </form>
    </section>
  );
}

export default function ImportarPageClient () {
  const { isAdmin } = useAuth();
  const handleImportProductos = (file: File) => importService.productos(file);
  const handleImportClientes = (file: File) => importService.clientes(file);

  if (!isAdmin) {
    return (
      <section>
        <h1 className='text-2xl font-semibold'>Importar CSV</h1>
        <p className='mt-2 text-slate-600'>Solo un administrador puede importar datos.</p>
      </section>
    );
  }

  return (
    <section>
      <h1 className='text-2xl font-semibold'>Importar CSV</h1>
      <p className='mt-2 text-slate-600'>
        Carga inicial de catálogo y clientes. Los registros existentes se actualizan por código o email.
      </p>

      <div className='mt-6 grid grid-cols-1 gap-6 lg:grid-cols-2'>
        <ImportCard
          title='Productos'
          columns='codigo,nombre,unidadMedida,precio,stockActual,stockMinimo,costo,categoria'
          onImport={handleImportProductos}
        />
        <ImportCard
          title='Clientes y saldos iniciales'
          columns='nombre,correoElectronico,cuit,maximoDescubierto,saldoInicial'
          onImport={handleImportClientes}
        />
      </div>

      <p className='mt-6 text-xs text-slate-500'>
        Usá punto o coma como separador decimal. Codigo y email son las claves de actualización.
      </p>
    </section>
  );
}
