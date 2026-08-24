package dan.ms.tp.dangateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import reactor.core.publisher.Mono;

class JwtAuthFilterTest {

    private static final String SECRET = "test-secret-para-gateway-2024-1234567890";

    private JwtAuthFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter(SECRET);
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    private MockServerWebExchange exchangeFor(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path));
    }

    private String token(long expiresInMs, String subject, String rol, Object clienteId) {
        return Jwts.builder()
                .subject(subject)
                .claim("nombre", "Admin")
                .claim("rol", rol)
                .claim("clienteId", clienteId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiresInMs))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @Test
    void healthCheckPath_ShouldBypassAuth() {
        MockServerWebExchange exchange = exchangeFor("/actuator/health");

        filter.filter(exchange, chain).block();

        verify(chain).filter(any(ServerWebExchange.class));
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void nonHealthActuatorPath_ShouldRequireAuth() {
        MockServerWebExchange exchange = exchangeFor("/actuator/env");

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    @Test
    void loginPath_ShouldBypassAuth() {
        MockServerWebExchange exchange = exchangeFor("/api/usuarios/login");

        filter.filter(exchange, chain).block();

        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    void loginPathWithServicePrefix_ShouldBypassAuth() {
        MockServerWebExchange exchange = exchangeFor("/clientes/api/usuarios/login");

        filter.filter(exchange, chain).block();

        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    void pathOnlyContainingLoginFragment_ShouldNotBypassAuth() {
        MockServerWebExchange exchange = exchangeFor("/api/usuarios/login/extra");

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    @Test
    void missingAuthorizationHeader_ShouldReturnUnauthorized() {
        MockServerWebExchange exchange = exchangeFor("/api/clientes");

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    @Test
    void invalidToken_ShouldReturnUnauthorized() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/clientes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    @Test
    void expiredToken_ShouldReturnUnauthorized() {
        String token = Jwts.builder()
                .subject("admin@example.com")
                .claim("rol", "ADMIN")
                .issuedAt(new Date(System.currentTimeMillis() - 60_000))
                .expiration(new Date(System.currentTimeMillis() - 30_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/clientes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    @Test
    void validToken_ShouldPropagateUserHeaders() {
        String token = token(3_600_000, "admin@example.com", "ADMIN", 1);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/clientes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        ServerWebExchange forwarded = captor.getValue();

        assertThat(forwarded.getRequest().getHeaders().getFirst("X-User-Email")).isEqualTo("admin@example.com");
        assertThat(forwarded.getRequest().getHeaders().getFirst("X-User-Nombre")).isEqualTo("Admin");
        assertThat(forwarded.getRequest().getHeaders().getFirst("X-User-Rol")).isEqualTo("ADMIN");
        assertThat(forwarded.getRequest().getHeaders().getFirst("X-User-ClienteId")).isEqualTo("1");
    }
}
