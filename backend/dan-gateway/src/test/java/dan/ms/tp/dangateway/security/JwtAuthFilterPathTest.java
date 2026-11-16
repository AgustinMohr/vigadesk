package dan.ms.tp.dangateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import reactor.core.publisher.Mono;

/**
 * La decision de autorizacion se toma sobre el path canonico, no sobre el texto
 * crudo: una variante codificada del mismo recurso no puede saltear una regla.
 */
class JwtAuthFilterPathTest {

    private static final String SECRET = "test-secret-para-gateway-2024-1234567890";

    private JwtAuthFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter(SECRET);
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    private String token(String rol) {
        return Jwts.builder()
                .subject("u@example.com")
                .claim("nombre", "U")
                .claim("rol", rol)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    /** Crea la peticion con el path exactamente como llega por la red (sin re-codificar). */
    private MockServerWebExchange exchange(HttpMethod method, String rawPath, String rol) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.method(method, URI.create("http://localhost" + rawPath))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(rol)));
    }

    private HttpStatus run(HttpMethod method, String rawPath, String rol) {
        MockServerWebExchange exchange = exchange(method, rawPath, rol);
        filter.filter(exchange, chain).block();
        return (HttpStatus) exchange.getResponse().getStatusCode();
    }

    @Test
    void helper_ShouldKeepRawEncodedPath() {
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/%75suarios", "CLIENTE");
        assertThat(exchange.getRequest().getPath().value()).isEqualTo("/api/%75suarios");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/usuarios",
            "/api/%75suarios",
            "/api/%75suarios/1",
            "/api/usuarios/",
            "//api/usuarios",
            "/api/./usuarios",
            "/api/pedidos/../usuarios",
            "/clientes/api/usuarios",
            "/clientes/api/%75suarios",
            "/clientes//api/usuarios",
            "/clientes/api/pedidos/../usuarios"
    })
    void clienteGetUsuariosInAnyVariant_ShouldBeForbidden(String rawPath) {
        assertThat(run(HttpMethod.GET, rawPath, "CLIENTE")).isEqualTo(HttpStatus.FORBIDDEN);
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/actuator/prometheus",
            "/actuator/%70rometheus",
            "/clientes/actuator/prometheus",
            "/productos/actuator/prometheus",
            "/pedidos/actuator/prometheus",
            "/pedidos/actuator/%70rometheus",
            "/clientes/actuator"
    })
    void clienteAndGuestGetActuatorInAnyVariant_ShouldBeForbidden(String rawPath) {
        assertThat(run(HttpMethod.GET, rawPath, "CLIENTE")).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(run(HttpMethod.GET, rawPath, "GUEST")).isEqualTo(HttpStatus.FORBIDDEN);
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    @Test
    void adminGetUsuariosEncoded_ShouldBeAllowed() {
        assertThat(run(HttpMethod.GET, "/api/%75suarios", "ADMIN")).isNull();
        verify(chain).filter(any(ServerWebExchange.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/usuarios%2Fx",
            "/api/usuarios%2fx",
            "/api/usuarios%5Cx",
            "/api/%2575suarios",
            "/api/%00usuarios",
            "/api/usuarios;jsessionid=1",
            "/api/pedidos/../../..",
            "/clientes/api/%252e%252e/usuarios"
    })
    void ambiguousPaths_ShouldBeRejectedForEveryRole(String rawPath) {
        assertThat(run(HttpMethod.GET, rawPath, "CLIENTE")).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(run(HttpMethod.GET, rawPath, "ADMIN")).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    @Test
    void clientePostPedidosWithTrailingSlash_ShouldStayAllowed() {
        assertThat(run(HttpMethod.POST, "/api/pedidos/", "CLIENTE")).isNull();
        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    void clientePostPedidosWithServicePrefix_ShouldStayAllowed() {
        assertThat(run(HttpMethod.POST, "/pedidos/api/pedidos", "CLIENTE")).isNull();
        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    void clientePostPedidosEncoded_ShouldStayAllowed() {
        assertThat(run(HttpMethod.POST, "/api/%70edidos", "CLIENTE")).isNull();
        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    void clientePostPedidosSubpath_ShouldBeForbidden() {
        assertThat(run(HttpMethod.POST, "/api/pedidos/abc/entregas", "CLIENTE")).isEqualTo(HttpStatus.FORBIDDEN);
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    @Test
    void clienteGetPdfWithEncodedSpaceInId_ShouldStayAllowed() {
        assertThat(run(HttpMethod.GET, "/api/pedidos/abc%20def/pdf", "CLIENTE")).isNull();
        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    void guestGetProductosEncoded_ShouldBeAllowedButNotClientes() {
        assertThat(run(HttpMethod.GET, "/api/%70roductos", "GUEST")).isNull();
        assertThat(run(HttpMethod.GET, "/api/%63lientes", "GUEST")).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
