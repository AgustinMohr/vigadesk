package isi.dan.msclientes.exception;

import dan.common.error.AccesoDenegadoException;

public class ForbiddenException extends AccesoDenegadoException {
    public ForbiddenException(String message) {
        super(message);
    }
}
