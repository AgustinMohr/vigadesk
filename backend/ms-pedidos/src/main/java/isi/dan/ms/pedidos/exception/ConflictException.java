package isi.dan.ms.pedidos.exception;

import dan.common.error.ConflictoException;

public class ConflictException extends ConflictoException {
    public ConflictException(String message) {
        super(message);
    }
}
