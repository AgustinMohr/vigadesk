package isi.dan.ms.pedidos.exception;

public class UnprocessableEntityException extends RuntimeException {

    public UnprocessableEntityException(String message) {
        super(message);
    }
}
