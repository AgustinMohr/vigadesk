package isi.dan.ms_productos.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import dan.common.error.ApiException;
import dan.common.error.BaseExceptionHandler;
import dan.common.error.ErrorResponse;

/** El formato y el mapeo general se prueban en dan-common; aca, las excepciones propias de ms-productos. */
class RestControllerExceptionTest {

    private final RestControllerException handler = new RestControllerException();

    private ServletWebRequest request(String path) {
        return new ServletWebRequest(new MockHttpServletRequest("GET", path));
    }

    @Test
    void productoNotFound_ShouldBe404WithItsMessage() {
        ResponseEntity<ErrorResponse> response =
                handler.handleApiException(new ProductoNotFoundException(99L), request("/api/productos/99"));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
        assertThat(response.getBody().message()).isEqualTo("Producto 99 no encontrado");
        assertThat(response.getBody().path()).isEqualTo("/api/productos/99");
    }

    @Test
    void insufficientStock_ShouldBe409WithStableCode() {
        // ms-pedidos distingue "sin stock" de un fallo de infraestructura por este estado y codigo
        ResponseEntity<ErrorResponse> response = handler.handleApiException(
                new InsufficientStockException("Stock insuficiente para el producto ID 1"), request("/api/productos/stock/reserve"));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().code()).isEqualTo("STOCK_INSUFICIENTE");
        assertThat(response.getBody().message()).contains("Stock insuficiente");
    }

    @Test
    void domainExceptions_ShouldBeApiExceptions() {
        assertThat(new ProductoNotFoundException(1L)).isInstanceOf(ApiException.class);
        assertThat(new InsufficientStockException("x")).isInstanceOf(ApiException.class);
    }

    @Test
    void unexpectedRuntimeException_ShouldBe500WithoutInternalMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleOtherExceptions(
                new RuntimeException("jdbc:postgresql://pg-dan/db user=x"), request("/api/productos"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().message()).isEqualTo(BaseExceptionHandler.MENSAJE_ERROR_INTERNO);
    }
}
