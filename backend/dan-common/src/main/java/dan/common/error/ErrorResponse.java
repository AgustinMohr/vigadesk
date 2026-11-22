package dan.common.error;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Formato unico de error de los microservicios: {@code {timestamp, status, code, message, path}}.
 * {@code errors} solo aparece en errores de validacion (campo y mensaje).
 * Los 5xx llevan un mensaje generico: nunca el de la excepcion interna.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(Instant timestamp, int status, String code, String message, String path,
        Map<String, String> errors) {

    public static ErrorResponse of(int status, String code, String message, String path) {
        return new ErrorResponse(Instant.now(), status, code, message, path, null);
    }
}
