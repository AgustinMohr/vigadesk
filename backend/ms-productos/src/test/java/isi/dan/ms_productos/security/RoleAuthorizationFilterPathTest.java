package isi.dan.ms_productos.security;

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
            "/api/productos%2Fx",
            "/api/productos%5Cx",
            "/api/%2570roductos",
            "/api/%00productos",
            "/api/productos;jsessionid=1",
            "/api/productos/../../..",
            "/api/%zz"
    })
    void ambiguousPaths_ShouldBeRejectedForEveryRole(String path) throws Exception {
        for (String rol : new String[] {"CLIENTE", "GUEST", "ADMIN"}) {
            Resultado r = run("GET", path, rol);
            assertThat(r.status()).isEqualTo(HttpStatus.BAD_REQUEST.value());
            assertThat(r.chainCalled()).isFalse();
        }
    }

    @Test
    void ambiguousPathWithoutRole_ShouldBeUnauthorizedFirst() throws Exception {
        assertThat(run("GET", "/api/productos%2Fx", null).status()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void encodedFeignStockPathWithoutRole_ShouldNotBeExempt() throws Exception {
        assertThat(run("PUT", "/api/productos/stock/%72eserve", null).status())
                .isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(run("PUT", "/api/productos/stock/reserve", null).chainCalled()).isTrue();
    }

    @Test
    void catalogReadsKeepWorkingForEveryRole() throws Exception {
        for (String rol : new String[] {"CLIENTE", "GUEST", "ADMIN"}) {
            assertThat(run("GET", "/api/productos", rol).chainCalled()).isTrue();
            assertThat(run("GET", "/api/%70roductos/7", rol).chainCalled()).isTrue();
            assertThat(run("GET", "/api/categorias", rol).chainCalled()).isTrue();
        }
    }

    @Test
    void writesStayForbiddenForNonAdminEvenWhenEncoded() throws Exception {
        assertThat(run("POST", "/api/%70roductos", "CLIENTE").status()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(run("PUT", "/api/productos/provision", "CLIENTE").status()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }
}
