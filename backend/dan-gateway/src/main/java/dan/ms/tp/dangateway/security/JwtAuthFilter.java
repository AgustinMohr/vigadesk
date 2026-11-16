package dan.ms.tp.dangateway.security;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import reactor.core.publisher.Mono;

/**
 * Global filter that validates the JWT on every request entering the gateway
 * and propagates the authenticated user's identity to downstream services
 * via {@code X-User-*} headers.
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final List<String> SERVICE_PREFIXES = List.of("/clientes", "/productos", "/pedidos");

    private final String secret;

    public JwtAuthFilter(@Value("${jwt.secret}") String secret) {
        this.secret = secret;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }

        String token = header.substring(7);
        try {
            var key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token)
                    .getPayload();

            // La autorizacion se decide sobre el path canonico: el enrutamiento y los
            // controladores interpretan el path decodificado, no el texto crudo.
            String canonicalPath = PathNormalizer.normalize(path);
            if (canonicalPath == null) {
                return badRequest(exchange);
            }

            String rol = claims.get("rol", String.class);
            if (!isAuthorized(exchange.getRequest().getMethod(), stripServicePrefix(canonicalPath), rol)) {
                return forbidden(exchange);
            }

            ServerWebExchange mutated = exchange.mutate()
                    .request(r -> r.headers(h -> {
                        h.set("X-User-Email", claims.getSubject() != null ? claims.getSubject() : "");
                        h.set("X-User-Nombre", claims.get("nombre", String.class) != null ? claims.get("nombre", String.class) : "");
                        h.set("X-User-Rol", claims.get("rol", String.class) != null ? claims.get("rol", String.class) : "");

                        Integer clienteId = toInteger(claims.get("clienteId"));
                        h.set("X-User-ClienteId", clienteId != null ? String.valueOf(clienteId) : "");
                    }))
                    .build();

            return chain.filter(mutated);

        } catch (JwtException | IllegalArgumentException e) {
            return unauthorized(exchange);
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }

    /**
     * Routes that do not require authentication.
     */
    private boolean isWhitelisted(String path) {
        return isHealthCheck(path) || isLoginPath(path);
    }

    /**
     * Only the liveness endpoint is exposed without authentication, so HAProxy,
     * Prometheus and Eureka can probe the gateway. Everything else under
     * {@code /actuator/*} stays behind auth.
     */
    private boolean isHealthCheck(String path) {
        return path.equals("/actuator/health");
    }

    /**
     * The login endpoint is the only public route. Match the exact paths the
     * gateway can receive: direct and prefixed by the clients service route.
     */
    private boolean isLoginPath(String path) {
        return path.equals("/api/usuarios/login") || path.equals("/clientes/api/usuarios/login");
    }

    /**
     * Autorizacion por rol. El gateway es la unica puerta de entrada del
     * trafico de usuarios; los microservicios confian en los headers X-User-*.
     * ADMIN puede todo. CLIENTE/GUEST solo lectura propia y alta de pedidos.
     */
    private boolean isAuthorized(HttpMethod method, String normalized, String rol) {
        if ("ADMIN".equalsIgnoreCase(rol)) {
            return true;
        }
        if (normalized.startsWith("/actuator")) {
            return false;
        }
        if (normalized.startsWith("/api/usuarios")) {
            return false;
        }
        if ("GUEST".equalsIgnoreCase(rol)) {
            // El invitado solo puede ver el catalogo publico. Nunca clientes,
            // obras, pedidos ni pagos.
            return isSafeMethod(method)
                    && (normalized.startsWith("/api/productos") || normalized.startsWith("/api/categorias"));
        }
        if (isSafeMethod(method)) {
            return true;
        }
        return HttpMethod.POST.equals(method)
                && (normalized.equals("/api/pedidos") || normalized.equals("/api/pedidos/"));
    }

    private boolean isSafeMethod(HttpMethod method) {
        return HttpMethod.GET.equals(method)
                || HttpMethod.HEAD.equals(method)
                || HttpMethod.OPTIONS.equals(method);
    }

    /**
     * Las rutas /clientes/**, /productos/** y /pedidos/** llegan al microservicio sin
     * el prefijo (RewritePath). Se aplican las mismas reglas que al path sin prefijo,
     * incluido /actuator, que un CLIENTE o GUEST nunca debe alcanzar por esa via.
     */
    private String stripServicePrefix(String path) {
        for (String prefix : SERVICE_PREFIXES) {
            if (path.equals(prefix)) {
                return "/";
            }
            if (path.startsWith(prefix + "/")) {
                return path.substring(prefix.length());
            }
        }
        return path;
    }

    private Mono<Void> badRequest(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] body = "{\"message\":\"Ruta invalida\"}".getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] body = "{\"message\":\"Token invalido o ausente\"}".getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    private Mono<Void> forbidden(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] body = "{\"message\":\"No tenes permisos para esta operacion\"}".getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    private Integer toInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }
}
