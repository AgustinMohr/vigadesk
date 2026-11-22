package isi.dan.msclientes.exception;

import dan.common.error.RecursoNoEncontradoException;

// Excepción lanzada cuando no se encuentra un usuario de cliente por ID
public class UsuarioNotFoundException extends RecursoNoEncontradoException {
    public UsuarioNotFoundException(String msg) {
        super(msg);
    }
}
