package isi.dan.ms.pedidos.exception;

import dan.common.error.RecursoNoEncontradoException;

public class PedidoNotFoundException extends RecursoNoEncontradoException {
    public PedidoNotFoundException(String id) {
        super("Pedido " + id + " no encontrado");
    }
}
