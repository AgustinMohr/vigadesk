package isi.dan.msclientes.exception;

import dan.common.error.RecursoNoEncontradoException;

// Excepción lanzada cuando no se encuentra un cliente por ID
public class ClienteNotFoundException extends RecursoNoEncontradoException {
    public ClienteNotFoundException(String msg) {
        super(msg);
    }
}
