package isi.dan.msclientes.security;

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
    void loginWithoutRole_isAllowed() throws Exception {
        assertThat(chainCalled(run("POST", "/api/usuarios/login", null))).isTrue();
    }

    @Test
    void feignSaldoWithoutRole_isAllowed() throws Exception {
        assertThat(chainCalled(run("GET", "/api/clientes/5/saldo", null))).isTrue();
    }

    @Test
    void missingRole_isUnauthorized() throws Exception {
        MockHttpServletResponse response = run("GET", "/api/clientes", null);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(chainCalled(response)).isFalse();
    }

    @Test
    void clienteGetClientes_isAllowed() throws Exception {
        assertThat(chainCalled(run("GET", "/api/clientes", "CLIENTE"))).isTrue();
    }

    @Test
    void guestGetClientes_isForbidden() throws Exception {
        MockHttpServletResponse response = run("GET", "/api/clientes", "GUEST");
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(chainCalled(response)).isFalse();
    }

    @Test
    void guestGetObras_isForbidden() throws Exception {
        MockHttpServletResponse response = run("GET", "/api/obras", "GUEST");
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(chainCalled(response)).isFalse();
    }

    @Test
    void clienteGetUsuarios_isForbidden() throws Exception {
        MockHttpServletResponse response = run("GET", "/api/usuarios", "CLIENTE");
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(chainCalled(response)).isFalse();
    }

    @Test
    void clientePostClientes_isForbidden() throws Exception {
        MockHttpServletResponse response = run("POST", "/api/clientes", "CLIENTE");
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(chainCalled(response)).isFalse();
    }

    @Test
    void adminPostClientes_isAllowed() throws Exception {
        assertThat(chainCalled(run("POST", "/api/clientes", "ADMIN"))).isTrue();
    }
}
