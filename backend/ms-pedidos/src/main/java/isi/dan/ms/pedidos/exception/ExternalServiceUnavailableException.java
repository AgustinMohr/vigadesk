package isi.dan.ms.pedidos.exception;

public class ExternalServiceUnavailableException extends RuntimeException {

    public ExternalServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
