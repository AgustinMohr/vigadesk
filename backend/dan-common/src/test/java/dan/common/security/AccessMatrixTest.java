package dan.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Suite unica de la matriz de acceso: la usan el gateway y los tres microservicios,
 * asi que estas reglas se prueban una sola vez.
 */
class AccessMatrixTest {

    @ParameterizedTest(name = "{0} {1} {2} -> {3}")
    @CsvSource({
            // ADMIN: todo
            "ADMIN, GET,    /api/clientes,                true",
            "ADMIN, POST,   /api/clientes,                true",
            "ADMIN, DELETE, /api/productos/1,             true",
            "ADMIN, PATCH,  /api/pedidos/1/estado,        true",
            "ADMIN, GET,    /api/usuarios,                true",
            "ADMIN, GET,    /actuator/prometheus,         true",
            "admin, DELETE, /api/usuarios/3,              true",
            // CLIENTE: lectura, alta de pedidos y nada mas
            "CLIENTE, GET,     /api/clientes,             true",
            "CLIENTE, HEAD,    /api/obras/by-cliente/7,   true",
            "CLIENTE, OPTIONS, /api/pedidos,              true",
            "CLIENTE, GET,     /api/pedidos/1/comprobante, true",
            "CLIENTE, POST,    /api/pedidos,              true",
            "CLIENTE, POST,    /api/pedidos/,             true",
            "CLIENTE, POST,    /api/pedidos/1/entregas,   false",
            "CLIENTE, POST,    /api/pedidos/1/reintentar-reserva, false",
            "CLIENTE, PATCH,   /api/pedidos/1/estado,     false",
            "CLIENTE, PUT,     /api/pedidos,              false",
            "CLIENTE, POST,    /api/clientes,             false",
            "CLIENTE, POST,    /api/productos,            false",
            "CLIENTE, DELETE,  /api/obras/1,              false",
            // usuarios y actuator: solo ADMIN
            "CLIENTE, GET,    /api/usuarios,              false",
            "CLIENTE, GET,    /api/usuarios/1,            false",
            "CLIENTE, DELETE, /api/usuarios/1,            false",
            "CLIENTE, GET,    /actuator/prometheus,       false",
            "CLIENTE, GET,    /actuator,                  false",
            "CLIENTE, GET,    /actuator/env,              false",
            "GUEST,   GET,    /actuator/prometheus,       false",
            // GUEST: solo el catalogo publico, en lectura
            "GUEST, GET,     /api/productos,              true",
            "GUEST, GET,     /api/productos/5,            true",
            "GUEST, HEAD,    /api/productos,              true",
            "GUEST, GET,     /api/categorias,             true",
            "GUEST, POST,    /api/productos,              false",
            "GUEST, DELETE,  /api/productos/1,            false",
            "GUEST, GET,     /api/clientes,               false",
            "GUEST, GET,     /api/obras,                  false",
            "GUEST, GET,     /api/pedidos,                false",
            "GUEST, POST,    /api/pedidos,                false",
            "GUEST, GET,     /api/usuarios,               false",
            // un rol desconocido se trata como CLIENTE
            "OTRO, GET,    /api/clientes,                 true",
            "OTRO, POST,   /api/pedidos,                  true",
            "OTRO, POST,   /api/clientes,                 false"
    })
    void isAuthorized_ShouldFollowTheMatrix(String rol, String method, String path, boolean expected) {
        assertThat(AccessMatrix.isAuthorized(rol, method, path)).isEqualTo(expected);
    }

    @Test
    void isAuthorized_ShouldDenyWhenRoleIsMissingAndRuleNeedsOne() {
        assertThat(AccessMatrix.isAuthorized(null, "POST", "/api/clientes")).isFalse();
        assertThat(AccessMatrix.isAuthorized(null, "GET", "/api/usuarios")).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "/clientes/api/clientes,        /api/clientes",
            "/productos/api/productos/1,    /api/productos/1",
            "/pedidos/api/pedidos/,         /api/pedidos/",
            "/pedidos,                      /",
            "/clientes/actuator/prometheus, /actuator/prometheus",
            "/api/clientes,                 /api/clientes",
            "/pedidosx/api,                 /pedidosx/api"
    })
    void sinPrefijoDeServicio_ShouldStripOnlyGatewayPrefixes(String path, String expected) {
        assertThat(AccessMatrix.sinPrefijoDeServicio(path)).isEqualTo(expected);
    }

    @Test
    void rulesApplyToPrefixedRoutesOnceStripped() {
        // /clientes/api/usuarios y /pedidos/actuator/env nunca deben alcanzar a un CLIENTE
        assertThat(AccessMatrix.isAuthorized("CLIENTE", "DELETE",
                AccessMatrix.sinPrefijoDeServicio("/clientes/api/usuarios/3"))).isFalse();
        assertThat(AccessMatrix.isAuthorized("CLIENTE", "GET",
                AccessMatrix.sinPrefijoDeServicio("/pedidos/actuator/env"))).isFalse();
        assertThat(AccessMatrix.isAuthorized("CLIENTE", "POST",
                AccessMatrix.sinPrefijoDeServicio("/pedidos/api/pedidos"))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/api/usuarios/login", "/clientes/api/usuarios/login"})
    void isPublicAtGateway_ShouldAllowOnlyHealthAndLogin(String path) {
        assertThat(AccessMatrix.isPublicAtGateway(path)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/prometheus", "/actuator/health/liveness", "/api/usuarios",
            "/api/usuarios/login/x", "/x/api/usuarios/login", "/clientes/api/usuarios/loginx", "/api/clientes"})
    void isPublicAtGateway_ShouldNotAllowAnythingElse(String path) {
        assertThat(AccessMatrix.isPublicAtGateway(path)).isFalse();
    }

    @ParameterizedTest(name = "{0} {1} -> {2}")
    @CsvSource({
            "OPTIONS, /api/pedidos,                           true",
            "GET,     /actuator/health,                       true",
            "GET,     /actuator/health/liveness,              true",
            "GET,     /actuator/prometheus,                   false",
            "POST,    /api/usuarios/login,                    true",
            "GET,     /api/usuarios,                          false",
            "GET,     /api/clientes/5/saldo,                  true",
            "GET,     /api/clientes/5/maximo-descubierto,     true",
            "GET,     /api/clientes/5/contexto-pedido,        true",
            "POST,    /api/clientes/5/saldo,                  false",
            "GET,     /api/clientes/abc/saldo,                false",
            "GET,     /api/clientes/5/%73aldo,                false",
            "GET,     /api/clientes/5,                        false",
            "PUT,     /api/productos/stock/check,             true",
            "PUT,     /api/productos/stock/reserve,           true",
            "PUT,     /api/productos/stock/release,           true",
            "GET,     /api/productos/stock/check,             false",
            "PUT,     /api/productos/stock/%63heck,           false",
            "PUT,     /api/productos/stock/otra,              false",
            "PUT,     /api/productos/1,                       false"
    })
    void skipsRoleFilter_ShouldExemptOnlyHealthLoginAndInternalRoutes(String method, String rawPath, boolean expected) {
        assertThat(AccessMatrix.skipsRoleFilter(method, rawPath)).isEqualTo(expected);
    }

    @Test
    void isSafeMethod_ShouldAcceptReadMethodsOnly() {
        assertThat(AccessMatrix.isSafeMethod("get")).isTrue();
        assertThat(AccessMatrix.isSafeMethod("HEAD")).isTrue();
        assertThat(AccessMatrix.isSafeMethod("OPTIONS")).isTrue();
        assertThat(AccessMatrix.isSafeMethod("POST")).isFalse();
        assertThat(AccessMatrix.isSafeMethod(null)).isFalse();
    }
}
