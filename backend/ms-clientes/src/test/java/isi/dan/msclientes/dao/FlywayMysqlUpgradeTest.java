package isi.dan.msclientes.dao;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * V3 y V4 sobre bases ya existentes (no vacias): una que quedo en V2 con tablas en mayusculas
 * y datos, y otra creada por Hibernate (tablas en minuscula y estado ENUM).
 */
@Testcontainers(disabledWithoutDocker = true)
class FlywayMysqlUpgradeTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @BeforeEach
    void baseVacia() throws SQLException {
        try (Connection c = DriverManager.getConnection(mysql.getJdbcUrl(), "root", mysql.getPassword());
                Statement s = c.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS " + mysql.getDatabaseName());
            s.execute("CREATE DATABASE " + mysql.getDatabaseName());
        }
    }

    private Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), "root", mysql.getPassword())
                .target(target)
                .load();
    }

    private void ejecutar(String... sentencias) throws SQLException {
        try (Connection c = DriverManager.getConnection(mysql.getJdbcUrl(), "root", mysql.getPassword());
                Statement s = c.createStatement()) {
            for (String sql : sentencias) {
                s.execute(sql);
            }
        }
    }

    private List<String> consultar(String sql) throws SQLException {
        List<String> filas = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(mysql.getJdbcUrl(), "root", mysql.getPassword());
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                filas.add(rs.getString(1));
            }
        }
        return filas;
    }

    private List<String> tablas() throws SQLException {
        return consultar("select table_name from information_schema.tables where table_schema = database() "
                + "and lower(table_name) like 'ms%cli%' order by binary table_name");
    }

    @Test
    void baseEnV2ConMayusculasYDatos_ShouldRenombrarConservarDatosYConvertirEstado() throws SQLException {
        flyway("2").migrate();
        assertThat(tablas()).contains("MS_CLI_CLIENTE", "MS_CLI_OBRA");
        ejecutar("INSERT INTO MS_CLI_CLIENTE (nombre, CORREO_ELECTRONICO, cuit, MAXIMO_DESCUBIERTO) "
                + "VALUES ('Vieja SA', 'vieja@mail.com', '30111222333', 500.00)");

        flyway("4").migrate();

        assertThat(tablas()).containsExactly("ms_cli_cliente", "ms_cli_obra", "ms_cli_pago", "ms_cli_usuario");
        assertThat(consultar("select nombre from ms_cli_cliente")).containsExactly("Vieja SA");
        assertThat(consultar("select column_type from information_schema.columns where table_schema = database() "
                + "and table_name = 'ms_cli_obra' and column_name = 'estado'"))
                .containsExactly("enum('HABILITADA','PENDIENTE','FINALIZADA')");
        assertThat(consultar("select version from flyway_schema_history where success = 1 order by installed_rank"))
                .containsExactly("1", "2", "3", "4");
    }

    @Test
    void baseYaEnMinusculaYEnum_ShouldAplicarV3YV4SinCambiarNada() throws SQLException {
        flyway("2").migrate();
        // Deja el esquema como lo crea Hibernate: tablas en minuscula y estado ENUM
        ejecutar("RENAME TABLE MS_CLI_CLIENTE TO ms_cli_cliente, MS_CLI_OBRA TO ms_cli_obra, "
                + "MS_CLI_USUARIO TO ms_cli_usuario, MS_CLI_PAGO TO ms_cli_pago",
                "ALTER TABLE ms_cli_obra MODIFY COLUMN estado ENUM('HABILITADA','PENDIENTE','FINALIZADA') NULL",
                "INSERT INTO ms_cli_cliente (nombre, CORREO_ELECTRONICO, cuit, MAXIMO_DESCUBIERTO) "
                        + "VALUES ('Hibernate SA', 'hib@mail.com', '30999888777', 100.00)");

        flyway("4").migrate();

        assertThat(tablas()).containsExactly("ms_cli_cliente", "ms_cli_obra", "ms_cli_pago", "ms_cli_usuario");
        assertThat(consultar("select nombre from ms_cli_cliente")).containsExactly("Hibernate SA");
        assertThat(consultar("select version from flyway_schema_history where success = 1 order by installed_rank"))
                .containsExactly("1", "2", "3", "4");
    }
}
