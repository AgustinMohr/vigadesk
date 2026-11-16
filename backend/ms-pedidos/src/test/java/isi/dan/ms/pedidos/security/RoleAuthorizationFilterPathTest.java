package isi.dan.ms.pedidos.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * La autorizacion se decide sobre el path canonico. MockHttpServletRequest conserva
 * el requestURI tal como llega, igual que Tomcat (sin decodificar).
 */
class RoleAuthorizationFilterPathTest {

    private final RoleAuthorizationFilter filter = new RoleAuthorizationFilter();

    private record Resultado(int status, boolean chainCalled) {
    }

    private Resultado run(String method, String path, String rol) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        if (rol != null) {
            request.addHeader("X-User-Rol", rol);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return new Resultado(response.getStatus(), chain.getRequest() != null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/prometheus", "/actuator/%70rometheus", "/actuator", "/actuator/env", "//actuator/prometheus"})
    void clienteAndGuestGetActuatorInAnyVariant_ShouldBeForbidden(String path) throws Exception {
        for (String rol : new String[] {"CLIENTE", "GUEST"}) {
            Resultado r = run("GET", path, rol);
            assertThat(r.status()).isEqualTo(HttpStatus.FORBIDDEN.value());
            assertThat(r.chainCalled()).isFalse();
        }
    }

    @Test
    void adminGetActuator_ShouldBeAllowed() throws Exception {
        assertThat(run("GET", "/actuator/prometheus", "ADMIN").chainCalled()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/pedidos%2Fx",
            "/api/pedidos%5Cx",
            "/api/%2570edidos",
            "/api/%00pedidos",
            "/api/pedidos;jsessionid=1",
            "/api/pedidos/../../..",
            "/api/%zz"
    })
    void ambiguousPaths_ShouldBeRejectedForEveryRole(String path) throws Exception {
        for (String rol : new String[] {"CLIENTE", "ADMIN"}) {
            Resultado r = run("GET", path, rol);
            assertThat(r.status()).isEqualTo(HttpStatus.BAD_REQUEST.value());
            assertThat(r.chainCalled()).isFalse();
        }
    }

    @Test
    void ambiguousPathWithoutRole_ShouldBeUnauthorizedFirst() throws Exception {
        assertThat(run("GET", "/api/pedidos%2Fx", null).status()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void clientePostPedidos_StaysAllowedInCanonicalVariants() throws Exception {
        assertThat(run("POST", "/api/pedidos", "CLIENTE").chainCalled()).isTrue();
        assertThat(run("POST", "/api/pedidos/", "CLIENTE").chainCalled()).isTrue();
        assertThat(run("POST", "/api/%70edidos", "CLIENTE").chainCalled()).isTrue();
        assertThat(run("POST", "//api//pedidos", "CLIENTE").chainCalled()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/pedidos/abc/entregas", "/api/pedidos/abc/%65ntregas", "/api/pedidos/x/../abc/entregas"})
    void clientePostSubpaths_ShouldBeForbidden(String path) throws Exception {
        Resultado r = run("POST", path, "CLIENTE");
        assertThat(r.status()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(r.chainCalled()).isFalse();
    }

    @Test
    void clienteMutationsEncodedAsPedidos_ShouldBeForbidden() throws Exception {
        assertThat(run("DELETE", "/api/%70edidos/abc", "CLIENTE").status()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(run("PATCH", "/api/pedidos/abc/%65stado", "CLIENTE").status()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void clienteGetPdfWithEncodedSpace_ShouldStayAllowed() throws Exception {
        assertThat(run("GET", "/api/pedidos/abc%20def/pdf", "CLIENTE").chainCalled()).isTrue();
    }
}
