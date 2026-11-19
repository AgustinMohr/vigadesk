package isi.dan.msclientes.dao;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import isi.dan.msclientes.model.Cliente;

/**
 * Las migraciones Flyway V1 y V2 corren sobre una base MySQL vacia y Hibernate
 * valida el esquema resultante (ddl-auto=validate), como en produccion.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        // Igual que docker-compose.yml (SPRING_JPA_DATABASE_PLATFORM)
        "spring.jpa.database-platform=org.hibernate.dialect.MySQL8Dialect"
})
class FlywayMysqlTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migraciones_ShouldAplicarseTodasSinError() {
        List<String> versiones = jdbcTemplate.queryForList(
                "select version from flyway_schema_history where success = 1 order by installed_rank",
                String.class);

        assertThat(versiones).contains("1", "2");
    }

    @Test
    void esquemaValidado_ShouldPermitirGuardarUnCliente() {
        Cliente c = new Cliente();
        c.setNombre("Flyway SA");
        c.setCorreoElectronico("flyway@mail.com");
        c.setCuit("30111222333");
        c.setMaximoDescubierto(BigDecimal.valueOf(1000));

        Cliente guardado = clienteRepository.save(c);

        assertThat(clienteRepository.findByIdAndHabilitadoTrue(guardado.getId())).isPresent();
    }
}
