/**
 * Dominio de pedido compartido por todas las pantallas: los estados, sus colores y sus agrupaciones salen de
 * este único módulo. Agregar un estado a `ESTADOS_PEDIDO` hace fallar `tsc` hasta que tenga color
 * (`CLASES_ESTADO_PEDIDO` es un Record exhaustivo), así no queda una pantalla desactualizada.
 */
export const ESTADOS_PEDIDO = [
  'RECIBIDO',
  'ACEPTADO',
  'EN_PREPARACION',
  'ENTREGA_PARCIAL',
  'ENTREGADO',
  'CANCELADO',
  'RECHAZADO',
] as const;

export type EstadoPedido = typeof ESTADOS_PEDIDO[number];

/** Clases de Tailwind de la "pill" de cada estado (texto y fondo). */
export const CLASES_ESTADO_PEDIDO: Record<EstadoPedido, string> = {
  RECIBIDO: 'text-yellow-700 bg-yellow-100',
  ACEPTADO: 'text-blue-700 bg-blue-100',
  EN_PREPARACION: 'text-indigo-700 bg-indigo-100',
  ENTREGA_PARCIAL: 'text-purple-700 bg-purple-100',
  ENTREGADO: 'text-green-700 bg-green-100',
  CANCELADO: 'text-red-700 bg-red-100',
  RECHAZADO: 'text-gray-700 bg-gray-100',
};

const CLASES_ESTADO_DESCONOCIDO = 'text-slate-700 bg-slate-100';

export function esEstadoPedido (valor: unknown): valor is EstadoPedido {
  return typeof valor === 'string' && (ESTADOS_PEDIDO as readonly string[]).includes(valor);
}

/** Color de la pill de un estado; un estado que el backend agregue y el frontend no conozca sale en gris neutro. */
export function claseDeEstadoPedido (estado: string): string {
  return esEstadoPedido(estado) ? CLASES_ESTADO_PEDIDO[estado] : CLASES_ESTADO_DESCONOCIDO;
}

/** Estados en los que un ADMIN puede registrar entregas. */
export const ESTADOS_CON_ENTREGA: readonly EstadoPedido[] = ['EN_PREPARACION', 'ENTREGA_PARCIAL'];

/** Estados en los que se puede cancelar el pedido. */
export const ESTADOS_CANCELABLES: readonly EstadoPedido[] = ['ACEPTADO', 'EN_PREPARACION'];

/** Estados finales: el pedido ya no avanza. */
export const ESTADOS_TERMINALES: readonly EstadoPedido[] = ['ENTREGADO', 'CANCELADO', 'RECHAZADO'];
