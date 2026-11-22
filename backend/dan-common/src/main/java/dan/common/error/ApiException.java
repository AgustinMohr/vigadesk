package dan.common.error;

/**
 * Error de negocio con estado HTTP y codigo estable. El mensaje es texto pensado para quien llama:
 * se devuelve tal cual en 4xx. Cada excepcion de dominio de los servicios extiende una subclase.
 */
public class ApiException extends RuntimeException {

    private final int status;
    private final String code;

    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public ApiException(int status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
