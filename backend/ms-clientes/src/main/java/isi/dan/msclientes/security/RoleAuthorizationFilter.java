package isi.dan.msclientes.security;

import java.io.IOException;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Autorizacion por rol para el trafico que ya paso por el gateway.
 * El gateway valida el JWT y propaga X-User-Rol. Sin ese header no se atiende.
 * Los endpoints internos de Feign quedan exentos para no romper la comunicacion
 * servicio-a-servicio.
 */
public class RoleAuthorizationFilter extends OncePerRequestFilter {

    private static final Pattern FEIGN_CLIENT_READ =
            Pattern.compile("^/api/clientes/\\d+/(saldo|maximo-descubierto|contexto-pedido)$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String rol = request.getHeader("X-User-Rol");
        if (rol == null || rol.isBlank()) {
            write(response, HttpStatus.UNAUTHORIZED, "Autenticacion requerida");
            return;
        }
        // La decision se toma sobre el path canonico: el controlador interpreta el path
        // decodificado, asi que una variante codificada no puede saltear una regla.
        String path = PathNormalizer.normalize(request.getRequestURI());
        if (path == null) {
            write(response, HttpStatus.BAD_REQUEST, "Ruta invalida");
            return;
        }
        if (!isAuthorized(request.getMethod(), path, rol)) {
            write(response, HttpStatus.FORBIDDEN, "No tenes permisos para esta operacion");
            return;
        }
        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if ("OPTIONS".equals(method)) {
            return true;
        }
        if (path.equals("/actuator/health") || path.startsWith("/actuator/health/")) {
            return true;
        }
        if (path.equals("/api/usuarios/login")) {
            return true;
        }
        return "GET".equals(method) && FEIGN_CLIENT_READ.matcher(path).matches();
    }

    private boolean isAuthorized(String method, String path, String rol) {
        if ("ADMIN".equalsIgnoreCase(rol)) {
            return true;
        }
        if ("GUEST".equalsIgnoreCase(rol)) {
            return false;
        }
        if (path.startsWith("/actuator")) {
            return false;
        }
        if (path.startsWith("/api/usuarios")) {
            return false;
        }
        return "GET".equals(method) || "HEAD".equals(method);
    }

    private void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
