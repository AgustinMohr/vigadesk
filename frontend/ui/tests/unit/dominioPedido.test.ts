import { describe, expect, it } from 'vitest';
import {
  CLASES_ESTADO_PEDIDO,
  claseDeEstadoPedido,
  ESTADOS_CANCELABLES,
  ESTADOS_CON_ENTREGA,
  ESTADOS_PEDIDO,
  ESTADOS_TERMINALES,
  esEstadoPedido,
  type EstadoPedido,
} from '@/lib/domain/pedido';

describe('dominio de pedido', () => {
  it('cada estado tiene su color, y solo los estados tienen color', () => {
    expect(Object.keys(CLASES_ESTADO_PEDIDO).sort()).toEqual([...ESTADOS_PEDIDO].sort());
    for (const estado of ESTADOS_PEDIDO) {
      expect(CLASES_ESTADO_PEDIDO[estado]).toMatch(/^text-\w+-700 bg-\w+-100$/);
    }
  });

  it('el flujo del pedido conserva su orden', () => {
    expect(ESTADOS_PEDIDO).toEqual(['RECIBIDO', 'ACEPTADO', 'EN_PREPARACION', 'ENTREGA_PARCIAL', 'ENTREGADO', 'CANCELADO', 'RECHAZADO']);
  });

  it('esEstadoPedido acepta los estados y rechaza el resto', () => {
    expect(esEstadoPedido('ENTREGADO')).toBe(true);
    expect(esEstadoPedido('entregado')).toBe(false);
    expect(esEstadoPedido('')).toBe(false);
    expect(esEstadoPedido(undefined)).toBe(false);
    expect(esEstadoPedido(3)).toBe(false);
  });

  it('un estado desconocido (agregado por el backend) sale en gris neutro, sin romper', () => {
    expect(claseDeEstadoPedido('EN_REVISION')).toBe('text-slate-700 bg-slate-100');
    expect(claseDeEstadoPedido('CANCELADO')).toBe(CLASES_ESTADO_PEDIDO.CANCELADO);
  });

  it('las agrupaciones solo contienen estados validos y son coherentes', () => {
    for (const grupo of [ESTADOS_CON_ENTREGA, ESTADOS_CANCELABLES, ESTADOS_TERMINALES]) {
      for (const estado of grupo) expect(esEstadoPedido(estado)).toBe(true);
    }
    expect(ESTADOS_CON_ENTREGA).toEqual(['EN_PREPARACION', 'ENTREGA_PARCIAL']);
    expect(ESTADOS_CANCELABLES).toEqual(['ACEPTADO', 'EN_PREPARACION']);
    // un estado terminal no se puede cancelar ni entregar
    for (const estado of ESTADOS_TERMINALES) {
      expect(ESTADOS_CANCELABLES).not.toContain(estado);
      expect(ESTADOS_CON_ENTREGA).not.toContain(estado);
    }
  });
});

describe('compilacion estricta del dominio', () => {
  it('un mapa que no cubre todos los estados no compila (tsc lo verifica con ts-expect-error)', () => {
    // @ts-expect-error falta cubrir los demas estados de EstadoPedido
    const incompleto: Record<EstadoPedido, string> = { RECIBIDO: 'x' };
    expect(Object.keys(incompleto)).toHaveLength(1);
  });

  it('un valor que no es un estado no se acepta como EstadoPedido', () => {
    // @ts-expect-error 'EN_REVISION' no es un EstadoPedido
    const invalido: EstadoPedido = 'EN_REVISION';
    expect(esEstadoPedido(invalido)).toBe(false);
  });
});
