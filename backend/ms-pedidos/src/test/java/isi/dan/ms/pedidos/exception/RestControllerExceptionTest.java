package isi.dan.ms.pedidos.exception;

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
    void handlePedidoNotFoundException_ShouldReturnNotFound() {
        PedidoNotFoundException ex = new PedidoNotFoundException("abc123");

        ResponseEntity<ErrorInfo> response = handler.handlePedidoNotFoundException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().description()).contains("abc123");
        assertThat(response.getBody().detalle()).isEqualTo("Pedido no encontrado");
        assertThat(response.getBody().codigo()).isEqualTo(404);
    }

    @Test
    void handleIllegalArgument_ShouldReturnBadRequest() {
        IllegalArgumentException ex = new IllegalArgumentException("Estado no valido");

        ResponseEntity<ErrorInfo> response = handler.handleIllegalArgument(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().detalle()).isEqualTo("Solicitud invalida");
        assertThat(response.getBody().codigo()).isEqualTo(400);
    }

    @Test
    void handleIllegalState_ShouldReturnInternalServerError() {
        IllegalStateException ex = new IllegalStateException("Error al comunicarse con clientes");

        ResponseEntity<ErrorInfo> response = handler.handleIllegalState(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().codigo()).isEqualTo(500);
    }

    @Test
    void handleOtherExceptions_ShouldReturnInternalServerError() {
        Exception ex = new RuntimeException("Error inesperado");

        ResponseEntity<ErrorInfo> response = handler.handleOtherExceptions(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().description()).contains("Error inesperado");
        assertThat(response.getBody().detalle()).isEqualTo("Error en el servidor");
        assertThat(response.getBody().codigo()).isEqualTo(500);
    }
}
