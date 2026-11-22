package dan.common.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Autorizacion por rol para los microservicios que ya pasaron por el gateway. El gateway valida el JWT
 * y propaga {@code X-User-Rol}; sin ese header no se atiende. Las reglas viven en {@link AccessMatrix}.
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
        if (!AccessMatrix.isAuthorized(rol, request.getMethod(), path)) {
            write(response, HttpStatus.FORBIDDEN, "No tenes permisos para esta operacion");
            return;
        }
        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return AccessMatrix.skipsRoleFilter(request.getMethod(), request.getRequestURI());
    }

    private void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
