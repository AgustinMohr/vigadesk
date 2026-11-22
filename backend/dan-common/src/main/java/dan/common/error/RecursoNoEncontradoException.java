package dan.common.error;

public class RecursoNoEncontradoException extends ApiException {

    public RecursoNoEncontradoException(String message) {
        super(404, "NOT_FOUND", message);
    }

    public RecursoNoEncontradoException(String message, Throwable cause) {
        super(404, "NOT_FOUND", message, cause);
    }

    protected RecursoNoEncontradoException(String code, String message) {
        super(404, code, message);
    }
}
