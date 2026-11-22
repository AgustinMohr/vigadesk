package isi.dan.ms.pedidos.exception;

import dan.common.error.ServicioNoDisponibleException;

/** Un servicio del que depende el pedido no respondio: 503. La causa va al log, no a la respuesta. */
public class ExternalServiceUnavailableException extends ServicioNoDisponibleException {
    public ExternalServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
