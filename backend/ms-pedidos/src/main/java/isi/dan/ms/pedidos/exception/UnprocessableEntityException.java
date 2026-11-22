package isi.dan.ms.pedidos.exception;

import dan.common.error.SolicitudNoProcesableException;

public class UnprocessableEntityException extends SolicitudNoProcesableException {
    public UnprocessableEntityException(String message) {
        super(message);
    }
}
