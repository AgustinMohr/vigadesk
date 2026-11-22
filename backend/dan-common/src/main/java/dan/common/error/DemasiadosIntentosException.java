package dan.common.error;

/** Se supero el limite de intentos: 429. */
public class DemasiadosIntentosException extends ApiException {

    public DemasiadosIntentosException(String message) {
        super(429, "TOO_MANY_REQUESTS", message);
    }
}
