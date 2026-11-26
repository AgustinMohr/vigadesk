package dan.ms.tp.dangateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import dan.common.web.IdPeticion;
import reactor.core.publisher.Mono;

/**
 * Punto de entrada del identificador de peticion: conserva el {@code X-Request-Id} recibido (o crea uno), lo
 * reenvia a los microservicios, lo devuelve al cliente y escribe una linea por peticion. Es un {@code WebFilter}
 * para cubrir tambien lo que se rechaza antes de enrutar. En el gateway el MDC solo se completa alrededor de
 * esa linea: los hilos reactivos se comparten entre peticiones.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RegistroPeticionWebFilter implements WebFilter {

    private static final Logger LOG = LoggerFactory.getLogger(RegistroPeticionWebFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String id = IdPeticion.aceptarOCrear(exchange.getRequest().getHeaders().getFirst(IdPeticion.HEADER));
        long inicio = System.nanoTime();

        ServerWebExchange conId = exchange.mutate()
                .request(r -> r.headers(h -> h.set(IdPeticion.HEADER, id)))
                .build();
        // Antes de enviar la respuesta: el microservicio devuelve el mismo header y no debe salir repetido
        conId.getResponse().beforeCommit(() -> {
            conId.getResponse().getHeaders().set(IdPeticion.HEADER, id);
            return Mono.empty();
        });

        return chain.filter(conId)
                .doOnSuccess(nada -> registrar(conId, id, inicio, null))
                .doOnError(error -> registrar(conId, id, inicio, error));
    }

    private void registrar(ServerWebExchange exchange, String id, long inicio, Throwable error) {
        var estado = exchange.getResponse().getStatusCode();
        // Con error, el estado lo decide despues el manejador de errores de WebFlux
        String codigo = error != null ? "error:" + error.getClass().getSimpleName()
                : String.valueOf(estado != null ? estado.value() : 200);
        try (var mdc = MDC.putCloseable(IdPeticion.CLAVE_MDC, id)) {
            LOG.info("{} {} {} {}ms", exchange.getRequest().getMethod(), exchange.getRequest().getPath().value(),
                    codigo, (System.nanoTime() - inicio) / 1_000_000);
        }
    }
}
