package dan.common.error;

public class AccesoDenegadoException extends ApiException {

    public AccesoDenegadoException(String message) {
        super(403, "FORBIDDEN", message);
    }

    public AccesoDenegadoException(String message, Throwable cause) {
        super(403, "FORBIDDEN", message, cause);
    }

    protected AccesoDenegadoException(String code, String message) {
        super(403, code, message);
    }
}
