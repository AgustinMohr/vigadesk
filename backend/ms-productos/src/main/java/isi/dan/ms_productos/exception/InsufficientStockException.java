package isi.dan.ms_productos.exception;

import dan.common.error.ConflictoException;

/** Stock insuficiente: 409 con codigo estable STOCK_INSUFICIENTE (ms-pedidos lo distingue de un fallo de infraestructura). */
public class InsufficientStockException extends ConflictoException {

    public static final String CODIGO = "STOCK_INSUFICIENTE";

    public InsufficientStockException(String message) {
        super(CODIGO, message);
    }
}
