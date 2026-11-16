package isi.dan.ms.pedidos.exception;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class RestControllerException {

    private static final Logger logger = LoggerFactory.getLogger(RestControllerException.class);

    @ExceptionHandler(PedidoNotFoundException.class)
    public ResponseEntity<ErrorInfo> handlePedidoNotFoundException(PedidoNotFoundException ex) {
        logger.error("ERROR Buscando Pedido", ex);
        String detalle = ex.getCause() == null ? "Pedido no encontrado" : ex.getCause().getMessage();
        return new ResponseEntity<ErrorInfo>(
            new ErrorInfo(Instant.now(), ex.getMessage(), detalle, HttpStatus.NOT_FOUND.value()),
            HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorInfo> handleForbidden(ForbiddenException ex) {
        logger.warn("ACCESO DENEGADO: {}", ex.getMessage());
        return new ResponseEntity<ErrorInfo>(
            new ErrorInfo(Instant.now(), ex.getMessage(), "Acceso denegado", HttpStatus.FORBIDDEN.value()),
            HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorInfo> handleIllegalArgument(IllegalArgumentException ex) {        logger.error("ERROR Argumento invalido", ex);
        String detalle = ex.getCause() == null ? "Solicitud invalida" : ex.getCause().getMessage();
        return new ResponseEntity<ErrorInfo>(
            new ErrorInfo(Instant.now(), ex.getMessage(), detalle, HttpStatus.BAD_REQUEST.value()),
            HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorInfo> handleIllegalState(IllegalStateException ex) {
        logger.error("ERROR Estado invalido", ex);
        String detalle = ex.getCause() == null ? "Error interno del servidor" : ex.getCause().getMessage();
        return new ResponseEntity<ErrorInfo>(
            new ErrorInfo(Instant.now(), ex.getMessage(), detalle, HttpStatus.INTERNAL_SERVER_ERROR.value()),
            HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(ExternalServiceUnavailableException.class)
    public ResponseEntity<ErrorInfo> handleExternalServiceUnavailable(ExternalServiceUnavailableException ex) {
        logger.error("SERVICIO EXTERNO NO DISPONIBLE: {}", ex.getMessage(), ex);
        return new ResponseEntity<ErrorInfo>(
            new ErrorInfo(Instant.now(), ex.getMessage(), "Servicio temporalmente no disponible", HttpStatus.SERVICE_UNAVAILABLE.value()),
            HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(UnprocessableEntityException.class)
    public ResponseEntity<ErrorInfo> handleUnprocessableEntity(UnprocessableEntityException ex) {
        logger.warn("SOLICITUD NO PROCESABLE: {}", ex.getMessage());
        return new ResponseEntity<ErrorInfo>(
            new ErrorInfo(Instant.now(), ex.getMessage(), "Solicitud no procesable", HttpStatus.UNPROCESSABLE_ENTITY.value()),
            HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorInfo> handleConflict(ConflictException ex) {
        logger.warn("CONFLICTO DE ESTADO: {}", ex.getMessage());
        return new ResponseEntity<ErrorInfo>(
            new ErrorInfo(Instant.now(), ex.getMessage(), "Conflicto de estado", HttpStatus.CONFLICT.value()),
            HttpStatus.CONFLICT);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            errors.put(error.getField(), error.getDefaultMessage())
        );
        return ResponseEntity.badRequest().body(errors);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorInfo> handleOtherExceptions(Exception ex) {
        logger.error("ERROR MS PEDIDOS", ex);
        String detalle = ex.getCause() == null ? "Error en el servidor" : ex.getCause().getMessage();
        return new ResponseEntity<ErrorInfo>(
            new ErrorInfo(Instant.now(), ex.getMessage(), detalle, HttpStatus.INTERNAL_SERVER_ERROR.value()),
            HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
