package dan.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ParametrosConsultaTest {

    @Test
    void pagina_Defaults() {
        assertThat(ParametrosConsulta.pagina(null)).isZero();
        assertThat(ParametrosConsulta.pagina(3)).isEqualTo(3);
        assertThatThrownBy(() -> ParametrosConsulta.pagina(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tamanio_RespetaElMaximo() {
        assertThat(ParametrosConsulta.tamanio(null)).isEqualTo(100);
        assertThat(ParametrosConsulta.tamanio(1)).isEqualTo(1);
        assertThat(ParametrosConsulta.tamanio(100)).isEqualTo(100);
        assertThatThrownBy(() -> ParametrosConsulta.tamanio(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ParametrosConsulta.tamanio(101)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100");
    }

    @Test
    void texto_RecortaYValidaLongitud() {
        assertThat(ParametrosConsulta.texto(null, "x")).isNull();
        assertThat(ParametrosConsulta.texto("   ", "x")).isNull();
        assertThat(ParametrosConsulta.texto("  abc ", "x")).isEqualTo("abc");
        assertThat(ParametrosConsulta.texto("a".repeat(100), "x")).hasSize(100);
        assertThatThrownBy(() -> ParametrosConsulta.texto("a".repeat(101), "nombre"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nombre");
    }

    @Test
    void escaparLike_EscapaComodines() {
        assertThat(ParametrosConsulta.escaparLike("50%_a\\b")).isEqualTo("50\\%\\_a\\\\b");
    }
}
