package isi.dan.ms_productos.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RoleAuthorizationFilterTest {

    private final RoleAuthorizationFilter filter = new RoleAuthorizationFilter();

    private MockHttpServletResponse run(String method, String path, String rol) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        if (rol != null) {
            request.addHeader("X-User-Rol", rol);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        response.setHeader("X-Chain-Called", String.valueOf(chain.getRequest() != null));
        return response;
    }

    private boolean chainCalled(MockHttpServletResponse response) {
        return "true".equals(response.getHeader("X-Chain-Called"));
    }

    @Test
    void healthWithoutRole_isAllowed() throws Exception {
        assertThat(chainCalled(run("GET", "/actuator/health", null))).isTrue();
    }

    @Test
    void feignStockCheckWithoutRole_isAllowed() throws Exception {
        assertThat(chainCalled(run("PUT", "/api/productos/stock/check", null))).isTrue();
    }

    @Test
    void missingRole_isUnauthorized() throws Exception {
        MockHttpServletResponse response = run("GET", "/api/productos", null);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(chainCalled(response)).isFalse();
    }

    @Test
    void clienteGetProductos_isAllowed() throws Exception {
        assertThat(chainCalled(run("GET", "/api/productos", "CLIENTE"))).isTrue();
    }

    @Test
    void clientePostProductos_isForbidden() throws Exception {
        MockHttpServletResponse response = run("POST", "/api/productos", "CLIENTE");
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(chainCalled(response)).isFalse();
    }

    @Test
    void adminDeleteProducto_isAllowed() throws Exception {
        assertThat(chainCalled(run("DELETE", "/api/productos/1", "ADMIN"))).isTrue();
    }
}
