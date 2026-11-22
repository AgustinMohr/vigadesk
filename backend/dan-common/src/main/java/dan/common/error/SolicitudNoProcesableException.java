package dan.common.error;

public class SolicitudNoProcesableException extends ApiException {

    public SolicitudNoProcesableException(String message) {
        super(422, "UNPROCESSABLE_ENTITY", message);
    }

    public SolicitudNoProcesableException(String message, Throwable cause) {
        super(422, "UNPROCESSABLE_ENTITY", message, cause);
    }

    protected SolicitudNoProcesableException(String code, String message) {
        super(422, code, message);
    }
}
