package isi.dan.ms.pedidos.exception;

import dan.common.error.AccesoDenegadoException;

public class ForbiddenException extends AccesoDenegadoException {
    public ForbiddenException(String message) {
        super(message);
    }
}
