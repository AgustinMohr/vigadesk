package isi.dan.msclientes.dao;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import isi.dan.msclientes.model.Cliente;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ClienteRepositoryTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired
    private ClienteRepository clienteRepository;

    private Cliente crearCliente(String nombre, String email) {
        Cliente c = new Cliente();
        c.setNombre(nombre);
        c.setCorreoElectronico(email);
        c.setCuit("30111222333");
        c.setMaximoDescubierto(BigDecimal.valueOf(500000));
        return c;
    }

    @Test
    void findByHabilitadoTrue_ShouldOnlyReturnActiveClientes() {
        Cliente activo = clienteRepository.save(crearCliente("Activo", "activo@mail.com"));
        Cliente borrado = clienteRepository.save(crearCliente("Borrado", "borrado@mail.com"));
        borrado.setHabilitado(false);
        borrado.setFechaBorrado(Instant.now());
        clienteRepository.save(borrado);

        List<Cliente> result = clienteRepository.findByHabilitadoTrue();

        assertThat(result).contains(activo).doesNotContain(borrado);
    }

    @Test
    void findByHabilitadoTrue_Paginado_ShouldReturnPagesWithTotalAndSkipDisabled() {
        for (int i = 0; i < 12; i++) {
            clienteRepository.save(crearCliente("Pag " + i, "pag" + i + "@mail.com"));
        }
        Cliente borrado = clienteRepository.save(crearCliente("Borrado", "borrado2@mail.com"));
        borrado.setHabilitado(false);
        clienteRepository.save(borrado);
        long activos = clienteRepository.findByHabilitadoTrue().size();

        var segunda = clienteRepository.findByHabilitadoTrue(
                org.springframework.data.domain.PageRequest.of(1, 5, org.springframework.data.domain.Sort.by("id")));

        assertThat(segunda.getTotalElements()).isEqualTo(activos);
        assertThat(segunda.getContent()).hasSize(5).doesNotContain(borrado);
    }

    @Test
    void findByIdAndHabilitadoTrue_WhenDisabled_ShouldReturnEmpty() {
        Cliente borrado = clienteRepository.save(crearCliente("Borrado", "borrado2@mail.com"));
        borrado.setHabilitado(false);
        clienteRepository.save(borrado);

        Optional<Cliente> result = clienteRepository.findByIdAndHabilitadoTrue(borrado.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void findByCorreoElectronico_ShouldReturnCliente() {
        Cliente c = clienteRepository.save(crearCliente("Mail", "unico@mail.com"));

        Optional<Cliente> result = clienteRepository.findByCorreoElectronico("unico@mail.com");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(c.getId());
    }
}
