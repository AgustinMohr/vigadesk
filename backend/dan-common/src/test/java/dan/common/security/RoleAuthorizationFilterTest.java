package dan.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * El filtro servlet que usan los tres microservicios: codigos de estado y exenciones.
 * MockHttpServletRequest conserva el requestURI tal como llega, igual que Tomcat (sin decodificar).
 */
class RoleAuthorizationFilterTest {

    private final RoleAuthorizationFilter filter = new RoleAuthorizationFilter();

    private record Resultado(int status, boolean chainCalled, String body) {
    }

    private Resultado run(String method, String path, String rol) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        if (rol != null) {
            request.addHeader("X-User-Rol", rol);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return new Resultado(response.getStatus(), chain.getRequest() != null, response.getContentAsString());
    }

    @Test
    void missingRole_ShouldBeUnauthorized() throws Exception {
        Resultado r = run("GET", "/api/clientes", null);
        assertThat(r.status()).isEqualTo(401);
        assertThat(r.chainCalled()).isFalse();
        assertThat(r.body()).isEqualTo("{\"message\":\"Autenticacion requerida\"}");
    }

    @Test
    void blankRole_ShouldBeUnauthorized() throws Exception {
        assertThat(run("GET", "/api/clientes", "  ").status()).isEqualTo(401);
    }

    @Test
    void allowedRequest_ShouldContinueTheChain() throws Exception {
        Resultado r = run("GET", "/api/clientes", "CLIENTE");
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.chainCalled()).isTrue();
    }

    @Test
    void deniedRequest_ShouldBeForbiddenWithoutCallingTheChain() throws Exception {
        Resultado r = run("POST", "/api/clientes", "CLIENTE");
        assertThat(r.status()).isEqualTo(403);
        assertThat(r.chainCalled()).isFalse();
        assertThat(r.body()).isEqualTo("{\"message\":\"No tenes permisos para esta operacion\"}");
    }

    @Test
    void guestCanReadTheCatalogButNothingElse() throws Exception {
        assertThat(run("GET", "/api/productos", "GUEST").chainCalled()).isTrue();
        assertThat(run("GET", "/api/clientes", "GUEST").status()).isEqualTo(403);
        assertThat(run("GET", "/api/obras", "GUEST").status()).isEqualTo(403);
        assertThat(run("GET", "/api/pedidos", "GUEST").status()).isEqualTo(403);
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
        assertThat(r.status()).isEqualTo(403);
        assertThat(r.chainCalled()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/prometheus", "/actuator/%70rometheus", "/actuator", "/actuator/env"})
    void clienteAndGuestGetActuatorInAnyVariant_ShouldBeForbidden(String path) throws Exception {
        for (String rol : new String[] {"CLIENTE", "GUEST"}) {
            Resultado r = run("GET", path, rol);
            assertThat(r.status()).isEqualTo(403);
            assertThat(r.chainCalled()).isFalse();
        }
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
        for (String rol : new String[] {"CLIENTE", "ADMIN", "GUEST"}) {
            Resultado r = run("GET", path, rol);
            assertThat(r.status()).isEqualTo(400);
            assertThat(r.chainCalled()).isFalse();
            assertThat(r.body()).isEqualTo("{\"message\":\"Ruta invalida\"}");
        }
    }

    @Test
    void ambiguousPathWithoutRole_ShouldBeUnauthorizedFirst() throws Exception {
        assertThat(run("GET", "/api/usuarios%2Fx", null).status()).isEqualTo(401);
    }

    @Test
    void healthLoginAndOptions_ShouldNotRequireRole() throws Exception {
        assertThat(run("GET", "/actuator/health", null).chainCalled()).isTrue();
        assertThat(run("GET", "/actuator/health/liveness", null).chainCalled()).isTrue();
        assertThat(run("POST", "/api/usuarios/login", null).chainCalled()).isTrue();
        assertThat(run("OPTIONS", "/api/pedidos", null).chainCalled()).isTrue();
    }

    @Test
    void feignRoutes_ShouldNotRequireRoleButEncodedVariantsDo() throws Exception {
        assertThat(run("GET", "/api/clientes/5/saldo", null).chainCalled()).isTrue();
        assertThat(run("GET", "/api/clientes/5/contexto-pedido", null).chainCalled()).isTrue();
        assertThat(run("PUT", "/api/productos/stock/reserve", null).chainCalled()).isTrue();
        assertThat(run("GET", "/api/clientes/5/%73aldo", null).status()).isEqualTo(401);
        assertThat(run("PUT", "/api/productos/stock/%72eserve", null).status()).isEqualTo(401);
    }

    @Test
    void clienteKeepsReadingAndCreatingPedidosInCanonicalVariants() throws Exception {
        assertThat(run("GET", "/api/%63lientes", "CLIENTE").chainCalled()).isTrue();
        assertThat(run("GET", "/api/pedidos/abc%20def/comprobante", "CLIENTE").chainCalled()).isTrue();
        assertThat(run("POST", "/api/pedidos", "CLIENTE").chainCalled()).isTrue();
        assertThat(run("POST", "/api/pedidos/", "CLIENTE").chainCalled()).isTrue();
        assertThat(run("POST", "/api/%70edidos", "CLIENTE").chainCalled()).isTrue();
        assertThat(run("POST", "/api/pedidos/1/entregas", "CLIENTE").status()).isEqualTo(403);
        assertThat(run("PATCH", "/api/%70edidos/1/estado", "CLIENTE").status()).isEqualTo(403);
    }
}
