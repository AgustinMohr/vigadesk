package dan.common.error;

public class ConflictoException extends ApiException {

    public ConflictoException(String message) {
        super(409, "CONFLICT", message);
    }

    public ConflictoException(String message, Throwable cause) {
        super(409, "CONFLICT", message, cause);
    }

    protected ConflictoException(String code, String message) {
        super(409, code, message);
    }
}
