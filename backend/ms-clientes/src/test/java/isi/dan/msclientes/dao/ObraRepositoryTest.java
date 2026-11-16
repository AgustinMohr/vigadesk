package isi.dan.msclientes.dao;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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
import isi.dan.msclientes.model.EstadoObra;
import isi.dan.msclientes.model.Obra;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ObraRepositoryTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired
    private ObraRepository obraRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    private Obra crearObra(String direccion) {
        Obra o = new Obra();
        o.setDireccion(direccion);
        o.setLat(0f);
        o.setLng(0f);
        o.setPresupuesto(BigDecimal.valueOf(1000));
        return o;
    }

    private Cliente crearCliente() {
        Cliente c = new Cliente();
        c.setNombre("Constructora Test");
        c.setCorreoElectronico("obra-test@mail.com");
        c.setCuit("30222333444");
        c.setMaximoDescubierto(BigDecimal.valueOf(1000000));
        return clienteRepository.save(c);
    }

    @Test
    void findByHabilitadoTrue_ShouldOnlyReturnActiveObras() {
        Obra activa = obraRepository.save(crearObra("Obra A"));
        Obra borrada = obraRepository.save(crearObra("Obra B"));
        borrada.setHabilitado(false);
        obraRepository.save(borrada);

        List<Obra> result = obraRepository.findByHabilitadoTrue();

        assertThat(result).contains(activa).doesNotContain(borrada);
    }

    @Test
    void findByIdAndHabilitadoTrue_ShouldReturnActiveObra() {
        Obra o = obraRepository.save(crearObra("Obra C"));

        Optional<Obra> result = obraRepository.findByIdAndHabilitadoTrue(o.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getDireccion()).isEqualTo("Obra C");
    }

    @Test
    void countByClienteIdAndEstadoAndHabilitadoTrue_ShouldCountSoloEseEstado() {
        Cliente cliente = crearCliente();
        Obra habilitada = obraRepository.save(crearObra("Obra H"));
        habilitada.setCliente(cliente);
        habilitada.setEstado(EstadoObra.HABILITADA);
        obraRepository.save(habilitada);
        Obra pendiente = obraRepository.save(crearObra("Obra P"));
        pendiente.setCliente(cliente);
        pendiente.setEstado(EstadoObra.PENDIENTE);
        obraRepository.save(pendiente);

        long habilitadas = obraRepository.countByClienteIdAndEstadoAndHabilitadoTrue(cliente.getId(), EstadoObra.HABILITADA);
        long pendientes = obraRepository.countByClienteIdAndEstadoAndHabilitadoTrue(cliente.getId(), EstadoObra.PENDIENTE);

        assertThat(habilitadas).isEqualTo(1);
        assertThat(pendientes).isEqualTo(1);
    }

    @Test
    void findByClienteIsNullAndHabilitadoTrue_ShouldReturnSoloSinAsignar() {
        Obra sinCliente = obraRepository.save(crearObra("Obra Libre"));
        Obra conCliente = obraRepository.save(crearObra("Obra Ocupada"));
        conCliente.setCliente(crearCliente());
        obraRepository.save(conCliente);

        List<Obra> result = obraRepository.findByClienteIsNullAndHabilitadoTrue();

        assertThat(result).contains(sinCliente).doesNotContain(conCliente);
    }
}
