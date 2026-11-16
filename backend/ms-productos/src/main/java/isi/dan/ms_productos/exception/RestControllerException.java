package isi.dan.ms_productos.exception;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

// Manejador global de excepciones para ms-productos
@ControllerAdvice
public class RestControllerException {

    private static final Logger logger = LoggerFactory.getLogger(RestControllerException.class);

    @ExceptionHandler(ProductoNotFoundException.class)
    public ResponseEntity<ErrorInfo> handleProductNotFoundException(ProductoNotFoundException ex) {
        logger.error("ERROR Buscando Producto", ex);
        String detalle = ex.getCause() == null ? "Producto no encontrado": ex.getCause().getMessage();

        return new ResponseEntity<ErrorInfo>(new ErrorInfo(Instant.now(),ex.getMessage(),detalle,HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
    }

    // Catch-all para cualquier excepción no manejada específicamente
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorInfo> handleOtherExceptions(Exception ex) {
        logger.error("ERROR MS PRODUCTOS", ex);
        String detalle = ex.getCause() == null ? "Error en producto": ex.getCause().getMessage();
        return new ResponseEntity<ErrorInfo>(new ErrorInfo(Instant.now(),ex.getMessage(),detalle ,HttpStatus.INTERNAL_SERVER_ERROR.value()), HttpStatus.INTERNAL_SERVER_ERROR);
    }
	@ExceptionHandler(InsufficientStockException.class)
	public ResponseEntity<Map<String, Object>> handleInsufficientStock(InsufficientStockException ex) {
		return badRequest(ex.getMessage());
	}

	// Reglas de negocio inválidas lanzadas desde ProductoService
	// (ej: descuento promocional fuera del rango 0-100).
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
		return badRequest(ex.getMessage());
	}

	private ResponseEntity<Map<String, Object>> badRequest(String mensaje) {
		Map<String, Object> respuesta = new HashMap<>();
		respuesta.put("timestamp", LocalDateTime.now());
		respuesta.put("status", HttpStatus.BAD_REQUEST.value());
		respuesta.put("error", "Bad Request");
		respuesta.put("mensaje", mensaje);

		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(respuesta);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
		Map<String, String> errors = new HashMap<>();
		ex.getBindingResult().getFieldErrors().forEach(error ->
			errors.put(error.getField(), error.getDefaultMessage())
		);
		return ResponseEntity.badRequest().body(errors);
	}
}
