package dan.common.error;

public class ServicioNoDisponibleException extends ApiException {

    public ServicioNoDisponibleException(String message) {
        super(503, "SERVICE_UNAVAILABLE", message);
    }

    public ServicioNoDisponibleException(String message, Throwable cause) {
        super(503, "SERVICE_UNAVAILABLE", message, cause);
    }

    protected ServicioNoDisponibleException(String code, String message) {
        super(503, code, message);
    }
}
