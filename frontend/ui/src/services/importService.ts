import { http } from './http';

export type ImportResult = {
  creados: number;
  actualizados: number;
  mensaje: string;
};

function formData (file: File): FormData {
  const data = new FormData();
  data.append('file', file);
  return data;
}

export const importService = {
  productos: (file: File) =>
    http.upload<ImportResult>('/api/productos/import', formData(file)),

  clientes: (file: File) =>
    http.upload<ImportResult>('/api/clientes/import', formData(file)),
};
