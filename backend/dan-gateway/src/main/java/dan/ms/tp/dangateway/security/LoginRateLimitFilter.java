package dan.ms.tp.dangateway.security;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import dan.common.security.AccessMatrix;
import dan.common.security.FailedAttemptLimiter;
import reactor.core.publisher.Mono;

/**
 * Freno de fuerza bruta sobre el login, por IP de origen: tras maxFailures respuestas 401 dentro de la
 * ventana, las siguientes se rechazan con 429 sin llegar a ms-clientes. El limite por usuario (que es el que
 * protege cada cuenta) vive en ms-clientes.
 *
 * <p>Limitacion conocida: el navegador llega al gateway a traves del BFF de Next.js y HAProxy, asi que la IP
 * que ve el gateway es la del proxy y es compartida por todos los usuarios. Por eso el umbral por defecto es
 * alto: sirve de freno global ante un ataque masivo, no de limite fino. No se confia en X-Forwarded-For
 * salvo que se active {@code login.trust-forwarded-for}, porque un cliente puede falsearlo.
 */
@Component
public class LoginRateLimitFilter implements GlobalFilter, Ordered {

    private final FailedAttemptLimiter limiter;
    private final boolean trustForwardedFor;

    public LoginRateLimitFilter(
            @Value("${login.ip-max-failures:30}") int maxFailures,
            @Value("${login.ip-window-seconds:60}") long windowSeconds,
            @Value("${login.trust-forwarded-for:false}") boolean trustForwardedFor) {
        this.limiter = new FailedAttemptLimiter(maxFailures, Duration.ofSeconds(windowSeconds));
        this.trustForwardedFor = trustForwardedFor;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!HttpMethod.POST.equals(exchange.getRequest().getMethod()) || !esLogin(path)) {
            return chain.filter(exchange);
        }

        String ip = ipDeOrigen(exchange);
        long espera = limiter.retryAfterSeconds(ip);
        if (espera > 0) {
            return tooManyRequests(exchange, path, espera);
        }
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            if (exchange.getResponse().getStatusCode() == HttpStatus.UNAUTHORIZED) {
                limiter.recordFailure(ip);
            }
        }));
    }

    @Override
    public int getOrder() {
        // Antes de JwtAuthFilter (-100): el login es publico, pero igual se limita
        return -200;
    }

    private boolean esLogin(String path) {
        return AccessMatrix.isPublicAtGateway(path) && path.endsWith("/login");
    }

    private String ipDeOrigen(ServerWebExchange exchange) {
        if (trustForwardedFor) {
            String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        InetSocketAddress remota = exchange.getRequest().getRemoteAddress();
        if (remota == null || remota.getAddress() == null) {
            return "desconocida";
        }
        return remota.getAddress().getHostAddress();
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange, String path, long espera) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set("Retry-After", String.valueOf(espera));

        String body = "{\"timestamp\":\"" + Instant.now() + "\",\"status\":429,\"code\":\"TOO_MANY_REQUESTS\","
                + "\"message\":\"Demasiados intentos fallidos. Intenta de nuevo en " + espera + " segundos\","
                + "\"path\":\"" + path + "\"}";
        return exchange.getResponse().writeWith(Mono.just(
                exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }
}
