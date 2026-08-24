package isi.dan.msclientes.exception;

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
    void handleClienteNotFoundException_ShouldReturnNotFound() {
        ClienteNotFoundException ex = new ClienteNotFoundException("Cliente 99 no encontrado");

        ResponseEntity<ErrorInfo> response = handler.handleClienteNotFoundException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().description()).contains("Cliente 99 no encontrado");
        assertThat(response.getBody().detalle()).isEqualTo("Cliente no encontrado");
        assertThat(response.getBody().codigo()).isEqualTo(404);
    }

    @Test
    void handleClienteNotFoundException_WithCause_ShouldUseCauseDetail() {
        ClienteNotFoundException ex = new ClienteNotFoundException("Error");
        ex.initCause(new RuntimeException("causa real"));

        ResponseEntity<ErrorInfo> response = handler.handleClienteNotFoundException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().detalle()).isEqualTo("causa real");
    }

    @Test
    void handleObraNotFoundException_ShouldReturnNotFound() {
        ObraNotFoundException ex = new ObraNotFoundException("Obra 99 no encontrada");

        ResponseEntity<ErrorInfo> response = handler.handleObraNotFoundException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().description()).contains("Obra 99 no encontrada");
        assertThat(response.getBody().codigo()).isEqualTo(404);
    }

    @Test
    void handleUsuarioNotFoundException_ShouldReturnNotFound() {
        UsuarioNotFoundException ex = new UsuarioNotFoundException("Usuario 99 no encontrado");

        ResponseEntity<ErrorInfo> response = handler.handleUsuarioNotFoundException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().description()).contains("Usuario 99 no encontrado");
        assertThat(response.getBody().detalle()).isEqualTo("Usuario no encontrado");
        assertThat(response.getBody().codigo()).isEqualTo(404);
    }

    @Test
    void handleRuntimeException_ShouldReturnBadRequest() {
        RuntimeException ex = new RuntimeException("Estado no valido");

        ResponseEntity<ErrorInfo> response = handler.handleRuntimeException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().description()).isEqualTo("Estado no valido");
        assertThat(response.getBody().codigo()).isEqualTo(400);
    }

    @Test
    void handleGenericException_ShouldReturnInternalServerError() {
        Exception ex = new RuntimeException("Error inesperado");

        ResponseEntity<ErrorInfo> response = handler.handleOtherExceptions(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().description()).contains("Error inesperado");
        assertThat(response.getBody().detalle()).isEqualTo("error en el servidor");
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
