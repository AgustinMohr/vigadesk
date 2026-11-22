package isi.dan.ms.pedidos.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import dan.common.error.ApiException;
import dan.common.error.BaseExceptionHandler;
import dan.common.error.ErrorResponse;

/** El formato y el mapeo general se prueban en dan-common; aca, las excepciones propias de ms-pedidos. */
class RestControllerExceptionTest {

    private final RestControllerException handler = new RestControllerException();

    private ResponseEntity<ErrorResponse> map(ApiException ex) {
        return handler.handleApiException(ex, new ServletWebRequest(new MockHttpServletRequest("GET", "/api/pedidos/1")));
    }

    @Test
    void pedidoNotFound_ShouldBe404() {
        ResponseEntity<ErrorResponse> r = map(new PedidoNotFoundException("abc"));
        assertThat(r.getStatusCode().value()).isEqualTo(404);
        assertThat(r.getBody().code()).isEqualTo("NOT_FOUND");
        assertThat(r.getBody().message()).isEqualTo("Pedido abc no encontrado");
        assertThat(r.getBody().path()).isEqualTo("/api/pedidos/1");
    }

    @Test
    void forbidden_ShouldBe403() {
        ResponseEntity<ErrorResponse> r = map(new ForbiddenException("No tenes permisos para crear pedidos"));
        assertThat(r.getStatusCode().value()).isEqualTo(403);
        assertThat(r.getBody().code()).isEqualTo("FORBIDDEN");
    }

    @Test
    void conflict_ShouldBe409() {
        ResponseEntity<ErrorResponse> r = map(new ConflictException("Estado invalido"));
        assertThat(r.getStatusCode().value()).isEqualTo(409);
        assertThat(r.getBody().code()).isEqualTo("CONFLICT");
    }

    @Test
    void unprocessable_ShouldBe422() {
        ResponseEntity<ErrorResponse> r = map(new UnprocessableEntityException("La obra no esta habilitada"));
        assertThat(r.getStatusCode().value()).isEqualTo(422);
        assertThat(r.getBody().code()).isEqualTo("UNPROCESSABLE_ENTITY");
    }

    @Test
    void externalServiceUnavailable_ShouldBe503WithoutTheCause() {
        ResponseEntity<ErrorResponse> r = map(new ExternalServiceUnavailableException(
                "No se pudo reservar el stock del pedido 7", new RuntimeException("Connection refused: pg-dan:5432")));
        assertThat(r.getStatusCode().value()).isEqualTo(503);
        assertThat(r.getBody().code()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(r.getBody().message()).isEqualTo("No se pudo reservar el stock del pedido 7");
    }

    @Test
    void illegalStateAndRuntime_ShouldBe500WithoutInternalMessage() {
        var request = new ServletWebRequest(new MockHttpServletRequest("GET", "/api/pedidos"));
        for (Exception ex : new Exception[] {new IllegalStateException("No se pudo generar el PDF: /tmp/x"),
                new RuntimeException("mongodb://root:secreto@mongo-db")}) {
            ResponseEntity<ErrorResponse> r = handler.handleOtherExceptions(ex, request);
            assertThat(r.getStatusCode().value()).isEqualTo(500);
            assertThat(r.getBody().message()).isEqualTo(BaseExceptionHandler.MENSAJE_ERROR_INTERNO);
        }
    }

    @Test
    void illegalArgument_ShouldBe400WithItsMessage() {
        var request = new ServletWebRequest(new MockHttpServletRequest("PATCH", "/api/pedidos/1/estado"));
        ResponseEntity<ErrorResponse> r = handler.handleIllegalArgument(
                new IllegalArgumentException("Solo se permite actualizar a ENTREGADO o CANCELADO"), request);
        assertThat(r.getStatusCode().value()).isEqualTo(400);
        assertThat(r.getBody().code()).isEqualTo("BAD_REQUEST");
    }
}
