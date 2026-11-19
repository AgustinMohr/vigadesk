package isi.dan.ms_productos.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import isi.dan.ms_productos.controller.ProductoController;
import isi.dan.ms_productos.modelo.Producto;

class RestControllerExceptionTest {

    private RestControllerException handler;

    @BeforeEach
    void setUp() {
        handler = new RestControllerException();
    }

    @Test
    void handleProductoNotFoundException_ShouldReturnNotFound() {
        ProductoNotFoundException ex = new ProductoNotFoundException(99L);

        ResponseEntity<ErrorInfo> response = handler.handleProductNotFoundException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().description()).contains("Producto 99 no encontrado");
        assertThat(response.getBody().detalle()).isEqualTo("Producto no encontrado");
        assertThat(response.getBody().codigo()).isEqualTo(404);
    }

    @Test
    void handleProductoNotFoundException_WithCause_ShouldUseCauseDetail() {
        ProductoNotFoundException ex = new ProductoNotFoundException(1L);
        ex.initCause(new RuntimeException("causa real"));

        ResponseEntity<ErrorInfo> response = handler.handleProductNotFoundException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().detalle()).isEqualTo("causa real");
    }

    @Test
    void handleGenericException_ShouldReturnInternalServerError() {
        Exception ex = new RuntimeException("Error inesperado");

        ResponseEntity<ErrorInfo> response = handler.handleOtherExceptions(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().description()).contains("Error inesperado");
        assertThat(response.getBody().detalle()).isEqualTo("Error en producto");
        assertThat(response.getBody().codigo()).isEqualTo(500);
    }

    @Test
    void handleGenericException_WithCause_ShouldUseCauseDetail() {
        Exception ex = new RuntimeException("Error", new IllegalArgumentException("causa especifica"));

        ResponseEntity<ErrorInfo> response = handler.handleOtherExceptions(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().detalle()).isEqualTo("causa especifica");
    }

    @Test
    void handleInsufficientStock_ShouldReturnConflictWithStableCode() {
        InsufficientStockException ex = new InsufficientStockException("Stock insuficiente para el producto ID 1");

        ResponseEntity<Map<String, Object>> response = handler.handleInsufficientStock(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsEntry("status", HttpStatus.CONFLICT.value());
        assertThat(response.getBody()).containsEntry("codigo", "STOCK_INSUFICIENTE");
        assertThat(response.getBody()).containsEntry("mensaje", "Stock insuficiente para el producto ID 1");
    }

    @Test
    void handleValidationExceptions_ShouldReturnBadRequestWithFieldErrors() throws Exception {
        Method method = ProductoController.class.getMethod(
                "createProducto", Producto.class, String.class, String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Producto(), "producto");
        bindingResult.addError(new FieldError("producto", "nombre", "El nombre es obligatorio"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<Map<String, String>> response = handler.handleValidationExceptions(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("nombre", "El nombre es obligatorio");
    }
}
