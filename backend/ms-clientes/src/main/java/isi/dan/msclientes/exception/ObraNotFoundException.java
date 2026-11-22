package isi.dan.msclientes.exception;

import dan.common.error.RecursoNoEncontradoException;

public class ObraNotFoundException extends RecursoNoEncontradoException {
    public ObraNotFoundException(String message) {
        super(message);
    }
}
