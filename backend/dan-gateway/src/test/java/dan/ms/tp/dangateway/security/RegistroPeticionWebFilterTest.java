package dan.ms.tp.dangateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

class RegistroPeticionWebFilterTest {

    private final RegistroPeticionWebFilter filtro = new RegistroPeticionWebFilter();

    private final AtomicReference<ServerWebExchange> reenviado = new AtomicReference<>();

    /** Hace de resto de la cadena: simula un microservicio que devuelve el mismo header. */
    private final WebFilterChain cadena = exchange -> {
        reenviado.set(exchange);
        String id = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
        exchange.getResponse().getHeaders().add("X-Request-Id", id);
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        return exchange.getResponse().setComplete();
    };

    private MockServerWebExchange filtrar(MockServerHttpRequest request) {
        var exchange = MockServerWebExchange.from(request);
        filtro.filter(exchange, cadena).block();
        return exchange;
    }

    @Test
    void creaUnId_LoReenviaAlServicioYLoDevuelveUnaSolaVez() {
        var exchange = filtrar(MockServerHttpRequest.get("/api/obras").build());

        String enviado = reenviado.get().getRequest().getHeaders().getFirst("X-Request-Id");
        assertThat(enviado).matches("[0-9a-f-]{36}");
        assertThat(exchange.getResponse().getHeaders().get("X-Request-Id")).containsExactly(enviado);
    }

    @Test
    void conservaUnIdAceptableDelCliente() {
        var exchange = filtrar(MockServerHttpRequest.get("/api/obras").header("X-Request-Id", "abc-1").build());

        assertThat(reenviado.get().getRequest().getHeaders().get("X-Request-Id")).containsExactly("abc-1");
        assertThat(exchange.getResponse().getHeaders().get("X-Request-Id")).containsExactly("abc-1");
    }

    @Test
    void reemplazaUnIdInaceptable() {
        filtrar(MockServerHttpRequest.get("/api/obras").header("X-Request-Id", "con espacios").build());

        assertThat(reenviado.get().getRequest().getHeaders().get("X-Request-Id"))
                .singleElement().asString().matches("[0-9a-f-]{36}");
    }

    @Test
    void siLaCadenaFallaElErrorSigueSuCurso() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/obras").build());

        Mono<Void> resultado = filtro.filter(exchange, e -> Mono.error(new IllegalStateException("caido")));

        assertThatThrownBy(resultado::block).isInstanceOf(IllegalStateException.class).hasMessage("caido");
    }
}
