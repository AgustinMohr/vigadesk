package dan.common.error;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Manejo de errores comun a los tres microservicios. Cada servicio lo extiende con un
 * {@code @RestControllerAdvice}; sus excepciones de dominio heredan de {@link ApiException} y salen
 * con su estado y codigo.
 *
 * <ul>
 *   <li>{@link ApiException}: su estado, codigo y mensaje.</li>
 *   <li>{@link IllegalArgumentException}: 400. Los servicios la usan para datos de entrada invalidos.</li>
 *   <li>Errores de Spring MVC (404 de ruta, 405, 415, cuerpo ilegible, parametros): su estado.</li>
 *   <li>Cualquier otra excepcion, incluida {@link RuntimeException}: 500 con mensaje generico.
 *       El detalle va al log, no a la respuesta.</li>
 * </ul>
 */
public abstract class BaseExceptionHandler extends ResponseEntityExceptionHandler {

    public static final String MENSAJE_ERROR_INTERNO = "Error interno del servidor";

    private static final Logger log = LoggerFactory.getLogger(BaseExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex, WebRequest request) {
        if (ex.getStatus() >= 500) {
            log.error("Error {} {}: {}", ex.getStatus(), ex.getCode(), ex.getMessage(), ex);
        } else {
            log.warn("Error {} {}: {}", ex.getStatus(), ex.getCode(), ex.getMessage());
        }
        return respuesta(ex.getStatus(), ex.getCode(), ex.getMessage(), request, null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, WebRequest request) {
        log.warn("Solicitud invalida: {}", ex.getMessage());
        return respuesta(400, "BAD_REQUEST", ex.getMessage(), request, null);
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return cuerpo(respuesta(413, "PAYLOAD_TOO_LARGE", "El archivo supera el tamano maximo permitido", request,
                null));
    }

    /** Ultima red: nada interno sale en la respuesta. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleOtherExceptions(Exception ex, WebRequest request) {
        log.error("Error no controlado en {}", pathDe(request), ex);
        return respuesta(500, "INTERNAL_ERROR", MENSAJE_ERROR_INTERNO, request, null);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errores = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> errores.putIfAbsent(error.getField(),
                error.getDefaultMessage() == null ? "Valor invalido" : error.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors().forEach(error -> errores.putIfAbsent(error.getObjectName(),
                error.getDefaultMessage() == null ? "Valor invalido" : error.getDefaultMessage()));
        return cuerpo(respuesta(400, "VALIDATION_ERROR", "Hay campos invalidos", request, errores));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return cuerpo(respuesta(400, "BAD_REQUEST", "El cuerpo de la solicitud no es valido", request, null));
    }

    /** Resto de errores de Spring MVC (ruta inexistente, metodo no permitido, parametros...). */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        int status = statusCode.value();
        String message;
        if (status >= 500) {
            log.error("Error {} en {}", status, pathDe(request), ex);
            message = MENSAJE_ERROR_INTERNO;
        } else if (body instanceof ProblemDetail detalle && detalle.getDetail() != null) {
            message = detalle.getDetail();
        } else {
            message = HttpStatus.valueOf(status).getReasonPhrase();
        }
        ResponseEntity<ErrorResponse> respuesta = respuesta(status, codigoDe(status), message, request, null);
        return ResponseEntity.status(statusCode).headers(headers).body(respuesta.getBody());
    }

    private ResponseEntity<ErrorResponse> respuesta(int status, String code, String message, WebRequest request,
            @Nullable Map<String, String> errores) {
        ErrorResponse body = new ErrorResponse(java.time.Instant.now(), status, code, message, pathDe(request),
                errores);
        return ResponseEntity.status(status).body(body);
    }

    private ResponseEntity<Object> cuerpo(ResponseEntity<ErrorResponse> respuesta) {
        return ResponseEntity.status(respuesta.getStatusCode()).body(respuesta.getBody());
    }

    private String pathDe(WebRequest request) {
        return request instanceof ServletWebRequest servlet ? servlet.getRequest().getRequestURI() : null;
    }

    private String codigoDe(int status) {
        return switch (status) {
            case 400 -> "BAD_REQUEST";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 406 -> "NOT_ACCEPTABLE";
            case 413 -> "PAYLOAD_TOO_LARGE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            case 503 -> "SERVICE_UNAVAILABLE";
            default -> status >= 500 ? "INTERNAL_ERROR" : "ERROR_" + status;
        };
    }
}
