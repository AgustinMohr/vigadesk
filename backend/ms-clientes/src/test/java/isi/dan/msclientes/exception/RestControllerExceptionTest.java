package isi.dan.msclientes.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import dan.common.error.ApiException;
import dan.common.error.BaseExceptionHandler;
import dan.common.error.ErrorResponse;

/** El formato y el mapeo general se prueban en dan-common; aca, las excepciones propias de ms-clientes. */
class RestControllerExceptionTest {

    private final RestControllerException handler = new RestControllerException();

    private ServletWebRequest request() {
        return new ServletWebRequest(new MockHttpServletRequest("GET", "/api/clientes/9"));
    }

    @Test
    void notFoundExceptions_ShouldBe404() {
        for (ApiException ex : new ApiException[] {new ClienteNotFoundException("Cliente 9 no encontrado"),
                new ObraNotFoundException("Obra 9 no encontrada"),
                new UsuarioNotFoundException("Usuario 9 no encontrado")}) {
            ResponseEntity<ErrorResponse> r = handler.handleApiException(ex, request());
            assertThat(r.getStatusCode().value()).isEqualTo(404);
            assertThat(r.getBody().code()).isEqualTo("NOT_FOUND");
            assertThat(r.getBody().message()).isEqualTo(ex.getMessage());
            assertThat(r.getBody().path()).isEqualTo("/api/clientes/9");
        }
    }

    @Test
    void forbidden_ShouldBe403() {
        ResponseEntity<ErrorResponse> r = handler.handleApiException(
                new ForbiddenException("No podes acceder a datos de otro cliente"), request());
        assertThat(r.getStatusCode().value()).isEqualTo(403);
        assertThat(r.getBody().code()).isEqualTo("FORBIDDEN");
    }

    @Test
    void runtimeException_ShouldBe500NotBadRequestAndHideTheMessage() {
        ResponseEntity<ErrorResponse> r = handler.handleOtherExceptions(
                new RuntimeException("could not execute statement [insert into ms_cli_cliente ...]"), request());
        assertThat(r.getStatusCode().value()).isEqualTo(500);
        assertThat(r.getBody().message()).isEqualTo(BaseExceptionHandler.MENSAJE_ERROR_INTERNO);
    }

    @Test
    void illegalArgument_ShouldBe400WithItsMessage() {
        ResponseEntity<ErrorResponse> r = handler.handleIllegalArgument(
                new IllegalArgumentException("El campo 'estado' es obligatorio"), request());
        assertThat(r.getStatusCode().value()).isEqualTo(400);
        assertThat(r.getBody().message()).isEqualTo("El campo 'estado' es obligatorio");
    }
}
