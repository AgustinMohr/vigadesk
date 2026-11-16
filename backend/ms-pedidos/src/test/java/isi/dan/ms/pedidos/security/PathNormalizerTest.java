package isi.dan.ms.pedidos.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PathNormalizerTest {

    @ParameterizedTest
    @CsvSource({
            "/api/usuarios,                  /api/usuarios",
            "/api/usuarios/,                 /api/usuarios/",
            "/api/%75suarios,                /api/usuarios",
            "/api/%75SUARIOS,                /api/uSUARIOS",
            "//api///usuarios,               /api/usuarios",
            "/api/./usuarios,                /api/usuarios",
            "/api/pedidos/../usuarios,       /api/usuarios",
            "/api/pedidos/.,                 /api/pedidos",
            "/api/pedidos/abc%20def/pdf,     /api/pedidos/abc def/pdf",
            "/api/productos/a+b,             /api/productos/a+b",
            "/,                              /",
            "/api/..,                        /"
    })
    void normalize_ShouldReturnCanonicalPath(String raw, String esperado) {
        assertThat(PathNormalizer.normalize(raw)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "api/usuarios",
            "/api/usuarios%2fx",
            "/api/usuarios%2Fx",
            "/api/usuarios%5cx",
            "/api/usuarios%5Cx",
            "/api/%00x",
            "/api/x%3bjsessionid",
            "/api/x;jsessionid=1",
            "/api/x\\y",
            "/api/%2575suarios",
            "/api/%zz",
            "/api/%",
            "/..",
            "/api/../..",
            "/api/%0d%0aX",
            "/api/%7f"
    })
    void normalize_ShouldRejectAmbiguousPaths(String raw) {
        assertThat(PathNormalizer.normalize(raw)).isNull();
    }

    @Test
    void normalize_Null_ShouldBeRejected() {
        assertThat(PathNormalizer.normalize(null)).isNull();
    }
}
