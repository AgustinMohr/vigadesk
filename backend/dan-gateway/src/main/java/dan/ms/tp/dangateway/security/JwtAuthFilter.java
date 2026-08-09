package dan.ms.tp.dangateway.security;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
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
 * Filtro Global de Seguridad JWT para Spring Cloud Gateway.
 * 
 * Implementa dos interfaces clave:
 *  - GlobalFilter: Aplica la lógica a TODAS las peticiones HTTP que ingresan al Gateway.
 *  - Ordered: Permite definir la prioridad de ejecución dentro de la cadena de filtros.
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    // Variable donde se guardará la clave secreta compartida para firmar/verificar JWTs.
    private final String secret;

    /**
     * Inyección de dependencias por constructor.
     * 
     * @param secret Lee la propiedad "jwt.secret" desde application.properties / application.yml
     */
    public JwtAuthFilter(@Value("${jwt.secret}") String secret) {
        this.secret = secret;
    }

    /**
     * Evalúa si una ruta de la URL está libre de autenticación (Lista Blanca).
     * 
     * @param path Ruta de la petición HTTP (ej: "/clientes/api/usuarios/login")
     * @return true si la ruta no requiere validación de token JWT, false en caso contrario.
     */
    private boolean isWhitelisted(String path) {
        // - "/actuator": Healthchecks para HAProxy, Prometheus, Eureka.
        // - "usuarios/login": Endpoint público donde el usuario se autentica para obtener el token.
        return path.startsWith("/actuator") || path.contains("usuarios/login");
    }

    /**
     * Método principal del filtro reactivo (Spring WebFlux).
     * Se ejecuta de forma asíncrona por cada petición entrante.
     * 
     * @param exchange Contiene el objeto request y response del ciclo HTTP.
     * @param chain Permite continuar la ejecución hacia el siguiente filtro o backend.
     * @return Mono<Void> Indica la finalización del procesamiento reactivo.
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1. Extrae la ruta de la petición entrante (ej: "/clientes/api/obras/5")
        String path = exchange.getRequest().getPath().value();

        // 2. Verificación de Lista Blanca: Si la ruta es pública, salta la validación de token.
        if (isWhitelisted(path)) {
            return chain.filter(exchange); // Deja pasar la petición intacta al microservicio.
        }

        // 3. Inspección de Cabeceras: Lee la cabecera "Authorization" del cliente HTTP.
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        
        // 4. Validación de Formato: Debe existir y comenzar estrictamente con el prefijo "Bearer "
        if (header == null || !header.startsWith("Bearer ")) {
            return unauthorized(exchange); // Corta el flujo y devuelve HTTP 401.
        }

        // 5. Extracción del token: Remueve los primeros 7 caracteres ("Bearer ") para aislar la cadena Base64 del JWT.
        String token = header.substring(7);
        try {
            // Convierte el string del secret en un objeto Key criptográfico HMAC-SHA.
            var key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            
            // Parsea y valida el token con la clave.
            // Si la firma es falsa o el token ya expiró (exp), lanza una excepción automáticamente.
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token)
                    .getPayload(); // Obtiene los datos internos (Claims) contenidos en el JSON del JWT.

            // 6. Propagación de Identidad (Mutación de la Petición):
            // Como las peticiones en WebFlux son inmutables, se usa .mutate() para crear una copia modificada.
            // Inyectamos cabeceras "X-User-*" con los datos validados del usuario.
            ServerWebExchange mutated = exchange.mutate()
                    .request(r -> r.headers(h -> {
                        // Email del usuario (guardado por defecto en el Subject del token)
                        h.set("X-User-Email", claims.getSubject() != null ? claims.getSubject() : "");
                        
                        // Propiedades personalizadas agregadas al payload del token
                        h.set("X-User-Nombre", claims.get("nombre", String.class) != null ? claims.get("nombre", String.class) : "");
                        h.set("X-User-Rol", claims.get("rol", String.class) != null ? claims.get("rol", String.class) : "");
                        
                        // ID del cliente asociado al usuario (si aplica)
                        Integer clienteId = claims.get("clienteId", Integer.class);
                        h.set("X-User-ClienteId", clienteId != null ? String.valueOf(clienteId) : "");
                    }))
                    .build();

            // 7. Reenvía la petición modificada con las nuevas cabeceras hacia el microservicio final.
            return chain.filter(mutated);

        } catch (JwtException | IllegalArgumentException e) {
            // Captura cualquier falla criptográfica (token alterado, firma inválida, fecha vencida).
            return unauthorized(exchange);
        }
    }

    /**
     * Construye y retorna una respuesta de error personalizada en formato JSON cuando la autenticación falla.
     * 
     * @param exchange Objeto del contexto HTTP para modificar la respuesta al cliente.
     * @return Mono<Void> Confirmación de escritura en la respuesta.
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        // Establece el código HTTP 401 Unauthorized
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        
        // Indica al cliente que la respuesta será un objeto JSON
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        
        // Cuerpo del mensaje de error codificado en bytes UTF-8
        byte[] body = "{\"message\":\"Token invalido o ausente\"}".getBytes(StandardCharsets.UTF_8);
        
        // Escribe los bytes directamente en el stream de respuesta reactivo
        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    /**
     * Define el orden de ejecución del filtro dentro de Spring Cloud Gateway.
     * 
     * @return -100 (Un número negativo asigna alta prioridad; se ejecuta antes de la reescritura de rutas).
     */
    @Override
    public int getOrder() {
        return -100;
    }
}