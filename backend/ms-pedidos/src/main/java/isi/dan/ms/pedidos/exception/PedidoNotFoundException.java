package isi.dan.ms.pedidos.exception;

public class PedidoNotFoundException extends RuntimeException {
    public PedidoNotFoundException(String id) {
        super("Pedido " + id + " no encontrado");
    }
}
