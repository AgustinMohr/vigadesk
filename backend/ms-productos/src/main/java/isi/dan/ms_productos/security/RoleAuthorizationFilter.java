package isi.dan.ms_productos.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Autorizacion por rol para ms-productos. El gateway propaga X-User-Rol.
 * El check de stock por Feign desde ms-pedidos queda exento.
 */
public class RoleAuthorizationFilter extends OncePerRequestFilter {

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
        return "PUT".equals(method) && (path.equals("/api/productos/stock/check")
                || path.equals("/api/productos/stock/reserve")
                || path.equals("/api/productos/stock/release"));
    }

    private boolean isAuthorized(String method, String path, String rol) {
        if ("ADMIN".equalsIgnoreCase(rol)) {
            return true;
        }
        if (path.startsWith("/actuator")) {
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
