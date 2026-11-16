package isi.dan.msclientes.exception;

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

    @ExceptionHandler(ClienteNotFoundException.class)
    public ResponseEntity<ErrorInfo> handleClienteNotFoundException(ClienteNotFoundException ex) {
        logger.error("ERROR Buscando Cliente", ex);
        String detalle = ex.getCause() == null ? "Cliente no encontrado" : ex.getCause().getMessage();

        return new ResponseEntity<ErrorInfo>(new ErrorInfo(Instant.now(), ex.getMessage(), detalle, HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(UsuarioNotFoundException.class)
    public ResponseEntity<ErrorInfo> handleUsuarioNotFoundException(UsuarioNotFoundException ex) {
        logger.error("ERROR Buscando Usuario", ex);
        String detalle = ex.getCause() == null ? "Usuario no encontrado" : ex.getCause().getMessage();

        return new ResponseEntity<ErrorInfo>(new ErrorInfo(Instant.now(), ex.getMessage(), detalle, HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ObraNotFoundException.class)
    public ResponseEntity<ErrorInfo> handleObraNotFoundException(ObraNotFoundException ex) {
        logger.error("ERROR Buscando Obra", ex);
        String detalle = ex.getCause() == null ? "Obra no encontrada" : ex.getCause().getMessage();

        return new ResponseEntity<ErrorInfo>(new ErrorInfo(Instant.now(), ex.getMessage(), detalle, HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorInfo> handleForbiddenException(ForbiddenException ex) {
        logger.warn("ACCESO DENEGADO: {}", ex.getMessage());
        return new ResponseEntity<ErrorInfo>(
                new ErrorInfo(Instant.now(), ex.getMessage(), "Acceso denegado", HttpStatus.FORBIDDEN.value()),
                HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorInfo> handleRuntimeException(RuntimeException ex) {
        logger.error("ERROR de negocio en MS CLIENTES", ex);
        return new ResponseEntity<ErrorInfo>(
                new ErrorInfo(Instant.now(), ex.getMessage(), ex.getMessage(), HttpStatus.BAD_REQUEST.value()),
                HttpStatus.BAD_REQUEST);
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
        logger.error("ERROR MS CLIENTES", ex);
        String detalle = ex.getCause() == null ? "error en el servidor" : ex.getCause().getMessage();
        return new ResponseEntity<ErrorInfo>(new ErrorInfo(Instant.now(), ex.getMessage(), detalle, HttpStatus.INTERNAL_SERVER_ERROR.value()), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
