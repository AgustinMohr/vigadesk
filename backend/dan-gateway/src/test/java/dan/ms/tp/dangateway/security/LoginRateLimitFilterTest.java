package dan.ms.tp.dangateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

class LoginRateLimitFilterTest {

    private static final String LOGIN = "/clientes/api/usuarios/login";

    private final GatewayFilterChain chain = mock(GatewayFilterChain.class);

    private MockServerWebExchange exchange(HttpMethod method, String path, String ip, String forwardedFor) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.method(method, path)
                .remoteAddress(new InetSocketAddress(ip, 5555));
        if (forwardedFor != null) {
            builder.header("X-Forwarded-For", forwardedFor);
        }
        return MockServerWebExchange.from(builder.build());
    }

    /** Hace que el servicio responda con el estado dado, como lo haria ms-clientes. */
    private void downstreamResponds(HttpStatus status) {
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(inv -> {
            ServerWebExchange ex = inv.getArgument(0);
            ex.getResponse().setStatusCode(status);
            return Mono.empty();
        });
    }

    private void login(LoginRateLimitFilter filter, String ip) {
        filter.filter(exchange(HttpMethod.POST, LOGIN, ip, null), chain).block();
    }

    @Test
    void underTheLimit_ShouldForwardTheRequest() {
        downstreamResponds(HttpStatus.UNAUTHORIZED);
        LoginRateLimitFilter filter = new LoginRateLimitFilter(3, 60, false);

        login(filter, "10.0.0.1");
        login(filter, "10.0.0.1");

        verify(chain, times(2)).filter(any(ServerWebExchange.class));
    }

    @Test
    void afterTooManyFailures_ShouldReturn429WithRetryAfterAndNotForward() {
        downstreamResponds(HttpStatus.UNAUTHORIZED);
        LoginRateLimitFilter filter = new LoginRateLimitFilter(3, 60, false);
        for (int i = 0; i < 3; i++) {
            login(filter, "10.0.0.1");
        }

        MockServerWebExchange bloqueado = exchange(HttpMethod.POST, LOGIN, "10.0.0.1", null);
        filter.filter(bloqueado, chain).block();

        assertThat(bloqueado.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(Long.parseLong(bloqueado.getResponse().getHeaders().getFirst("Retry-After"))).isBetween(1L, 60L);
        String body = bloqueado.getResponse().getBodyAsString().block();
        assertThat(body).contains("\"status\":429").contains("\"code\":\"TOO_MANY_REQUESTS\"")
                .contains("\"path\":\"" + LOGIN + "\"");
        verify(chain, times(3)).filter(any(ServerWebExchange.class)); // el 4to no se reenvio
    }

    @Test
    void successfulLogins_ShouldNotCountAsFailures() {
        downstreamResponds(HttpStatus.OK);
        LoginRateLimitFilter filter = new LoginRateLimitFilter(2, 60, false);
        for (int i = 0; i < 10; i++) {
            login(filter, "10.0.0.1");
        }

        verify(chain, times(10)).filter(any(ServerWebExchange.class));
    }

    @Test
    void differentIps_ShouldBeLimitedSeparately() {
        downstreamResponds(HttpStatus.UNAUTHORIZED);
        LoginRateLimitFilter filter = new LoginRateLimitFilter(2, 60, false);
        login(filter, "10.0.0.1");
        login(filter, "10.0.0.1");

        MockServerWebExchange otra = exchange(HttpMethod.POST, LOGIN, "10.0.0.2", null);
        filter.filter(otra, chain).block();

        assertThat(otra.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED); // pasó al servicio
    }

    @Test
    void nonLoginRequests_ShouldNeverBeLimited() {
        downstreamResponds(HttpStatus.UNAUTHORIZED);
        LoginRateLimitFilter filter = new LoginRateLimitFilter(1, 60, false);

        for (int i = 0; i < 5; i++) {
            filter.filter(exchange(HttpMethod.GET, "/clientes/api/clientes", "10.0.0.1", null), chain).block();
            filter.filter(exchange(HttpMethod.GET, LOGIN, "10.0.0.1", null), chain).block();
            filter.filter(exchange(HttpMethod.POST, "/api/pedidos", "10.0.0.1", null), chain).block();
        }

        verify(chain, times(15)).filter(any(ServerWebExchange.class));
    }

    @Test
    void forwardedFor_ShouldBeIgnoredUnlessTrusted() {
        downstreamResponds(HttpStatus.UNAUTHORIZED);
        LoginRateLimitFilter filter = new LoginRateLimitFilter(2, 60, false);
        // el atacante cambia X-Forwarded-For en cada intento para evadir el limite
        filter.filter(exchange(HttpMethod.POST, LOGIN, "10.0.0.1", "1.1.1.1"), chain).block();
        filter.filter(exchange(HttpMethod.POST, LOGIN, "10.0.0.1", "2.2.2.2"), chain).block();

        MockServerWebExchange tercero = exchange(HttpMethod.POST, LOGIN, "10.0.0.1", "3.3.3.3");
        filter.filter(tercero, chain).block();

        assertThat(tercero.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void forwardedFor_WhenTrusted_ShouldKeyByItsFirstEntry() {
        downstreamResponds(HttpStatus.UNAUTHORIZED);
        LoginRateLimitFilter filter = new LoginRateLimitFilter(2, 60, true);
        filter.filter(exchange(HttpMethod.POST, LOGIN, "10.0.0.1", "9.9.9.9, 10.0.0.1"), chain).block();
        filter.filter(exchange(HttpMethod.POST, LOGIN, "10.0.0.1", "9.9.9.9, 10.0.0.1"), chain).block();

        MockServerWebExchange mismoCliente = exchange(HttpMethod.POST, LOGIN, "10.0.0.1", "9.9.9.9");
        MockServerWebExchange otroCliente = exchange(HttpMethod.POST, LOGIN, "10.0.0.1", "8.8.8.8");
        filter.filter(mismoCliente, chain).block();
        filter.filter(otroCliente, chain).block();

        assertThat(mismoCliente.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(otroCliente.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void requestWithoutRemoteAddress_ShouldNotBreak() {
        downstreamResponds(HttpStatus.UNAUTHORIZED);
        LoginRateLimitFilter filter = new LoginRateLimitFilter(5, 60, false);
        MockServerWebExchange sinOrigen = MockServerWebExchange.from(MockServerHttpRequest.post(LOGIN).build());

        filter.filter(sinOrigen, chain).block();

        verify(chain).filter(any(ServerWebExchange.class));
        verify(chain, never()).filter(null);
    }

    @Test
    void order_ShouldRunBeforeJwtAuthFilter() {
        assertThat(new LoginRateLimitFilter(5, 60, false).getOrder()).isLessThan(new JwtAuthFilter("x".repeat(40)).getOrder());
    }
}
