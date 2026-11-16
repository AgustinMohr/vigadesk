package isi.dan.msclientes.security;

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
    @ValueSource(strings = {
            "/api/usuarios",
            "/api/%75suarios",
            "/api/%75suarios/1",
            "/api/usuarios/",
            "//api/usuarios",
            "/api/./usuarios",
            "/api/clientes/../usuarios"
    })
    void clienteGetUsuariosInAnyVariant_ShouldBeForbidden(String path) throws Exception {
        Resultado r = run("GET", path, "CLIENTE");
        assertThat(r.status()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(r.chainCalled()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/prometheus", "/actuator/%70rometheus", "/actuator", "/actuator/env"})
    void clienteGetActuatorInAnyVariant_ShouldBeForbidden(String path) throws Exception {
        Resultado r = run("GET", path, "CLIENTE");
        assertThat(r.status()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(r.chainCalled()).isFalse();
    }

    @Test
    void adminGetActuatorAndUsuariosEncoded_ShouldBeAllowed() throws Exception {
        assertThat(run("GET", "/actuator/prometheus", "ADMIN").chainCalled()).isTrue();
        assertThat(run("GET", "/api/%75suarios", "ADMIN").chainCalled()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/usuarios%2Fx",
            "/api/usuarios%5Cx",
            "/api/%2575suarios",
            "/api/%00usuarios",
            "/api/usuarios;jsessionid=1",
            "/api/clientes/../../..",
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
        assertThat(run("GET", "/api/usuarios%2Fx", null).status()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void encodedFeignSaldoWithoutRole_ShouldNotBeExempt() throws Exception {
        assertThat(run("GET", "/api/clientes/5/%73aldo", null).status()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(run("GET", "/api/clientes/5/saldo", null).chainCalled()).isTrue();
    }

    @Test
    void clienteGetClientesEncodedAndWithSpaceInId_ShouldStayAllowed() throws Exception {
        assertThat(run("GET", "/api/%63lientes", "CLIENTE").chainCalled()).isTrue();
        assertThat(run("GET", "/api/obras/by-cliente/7", "CLIENTE").chainCalled()).isTrue();
    }
}
