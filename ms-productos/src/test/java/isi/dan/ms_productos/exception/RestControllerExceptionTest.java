package isi.dan.ms_productos.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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
}
