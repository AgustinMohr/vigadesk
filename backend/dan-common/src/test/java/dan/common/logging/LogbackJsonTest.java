package dan.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.ConsoleAppender;

@SpringBootTest(classes = LogbackJsonTest.Config.class, properties = {
        "spring.application.name=servicio-de-prueba",
        "logging.config=classpath:logback-json-prueba.xml"
})
@ExtendWith(OutputCaptureExtension.class)
class LogbackJsonTest {

    @SpringBootConfiguration
    static class Config {
    }

    private static final Logger LOG = LoggerFactory.getLogger(LogbackJsonTest.class);

    @Test
    void cadaEventoEsUnaLineaJsonConNivelAplicacionYMdc(CapturedOutput salida) throws Exception {
        try (var id = MDC.putCloseable("requestId", "abc-123")) {
            LOG.info("pedido {} creado\ncon segunda linea", 7);
        }

        String linea = salida.getOut().lines().filter(l -> l.contains("creado")).findFirst().orElseThrow();
        JsonNode evento = new ObjectMapper().readTree(linea);
        assertThat(evento.get("level").asText()).isEqualTo("INFO");
        assertThat(evento.get("app").asText()).isEqualTo("servicio-de-prueba");
        assertThat(evento.get("requestId").asText()).isEqualTo("abc-123");
        assertThat(evento.get("message").asText()).isEqualTo("pedido 7 creado\ncon segunda linea");
        assertThat(evento.get("logger_name").asText()).isEqualTo(LogbackJsonTest.class.getName());
        assertThat(evento.has("@timestamp")).isTrue();
    }

    @Test
    void elNivelPorDefectoEsInfo(CapturedOutput salida) {
        LOG.debug("detalle que no debe salir");

        assertThat(salida.getOut()).doesNotContain("detalle que no debe salir");
    }

    @Test
    void soloSeEscribeAStdoutSinAppendersDeRed() {
        var raiz = ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(Logger.ROOT_LOGGER_NAME);

        assertThat(raiz.iteratorForAppenders()).toIterable()
                .singleElement().isInstanceOf(ConsoleAppender.class);
    }
}
