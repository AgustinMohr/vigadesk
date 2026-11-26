package dan.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;

class RegistroPeticionFilterTest {

    private final RegistroPeticionFilter filtro = new RegistroPeticionFilter();
    private final ListAppender<ILoggingEvent> eventos = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(RegistroPeticionFilter.class);

    @BeforeEach
    void capturar() {
        eventos.start();
        logger.addAppender(eventos);
    }

    @AfterEach
    void soltar() {
        logger.detachAppender(eventos);
    }

    private MockHttpServletRequest peticion(String idEntrante) {
        var request = new MockHttpServletRequest("GET", "/api/clientes");
        request.setQueryString("nombre=Juana");
        if (idEntrante != null) {
            request.addHeader(IdPeticion.HEADER, idEntrante);
        }
        return request;
    }

    @Test
    void conservaElIdEntranteEnElMdcYEnLaRespuestaYLimpiaAlTerminar() throws Exception {
        var response = new MockHttpServletResponse();
        String[] dentro = new String[1];

        filtro.doFilter(peticion("pedido-42"), response, (req, res) -> dentro[0] = MDC.get(IdPeticion.CLAVE_MDC));

        assertThat(dentro[0]).isEqualTo("pedido-42");
        assertThat(response.getHeader(IdPeticion.HEADER)).isEqualTo("pedido-42");
        assertThat(MDC.get(IdPeticion.CLAVE_MDC)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "con espacios", "linea\nfalsa", "<script>",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" })
    void unIdInaceptableSeReemplazaPorUnoNuevo(String idEntrante) throws Exception {
        var response = new MockHttpServletResponse();

        filtro.doFilter(peticion(idEntrante), response, (req, res) -> { });

        assertThat(response.getHeader(IdPeticion.HEADER)).isNotEqualTo(idEntrante).matches("[0-9a-f-]{36}");
    }

    @Test
    void sinHeaderCreaUnIdDistintoPorPeticion() throws Exception {
        var primera = new MockHttpServletResponse();
        var segunda = new MockHttpServletResponse();

        filtro.doFilter(peticion(null), primera, (req, res) -> { });
        filtro.doFilter(peticion(null), segunda, (req, res) -> { });

        assertThat(primera.getHeader(IdPeticion.HEADER)).isNotBlank()
                .isNotEqualTo(segunda.getHeader(IdPeticion.HEADER));
    }

    @Test
    void escribeUnaLineaPorPeticionSinLaQueryString() throws Exception {
        var response = new MockHttpServletResponse();

        filtro.doFilter(peticion("abc"), response, (req, res) -> response.setStatus(403));

        assertThat(eventos.list).singleElement().satisfies(evento -> {
            assertThat(evento.getFormattedMessage()).matches("GET /api/clientes 403 \\d+ms").doesNotContain("Juana");
            assertThat(evento.getMDCPropertyMap()).containsEntry(IdPeticion.CLAVE_MDC, "abc");
        });
    }

    @Test
    void siLaCadenaFallaRegistra500YLimpiaElMdc() {
        assertThatThrownBy(() -> filtro.doFilter(peticion("abc"), new MockHttpServletResponse(), (req, res) -> {
            throw new ServletException("falla");
        })).isInstanceOf(ServletException.class);

        assertThat(eventos.list).singleElement()
                .satisfies(e -> assertThat(e.getFormattedMessage()).contains(" 500 "));
        assertThat(MDC.get(IdPeticion.CLAVE_MDC)).isNull();
    }
}
