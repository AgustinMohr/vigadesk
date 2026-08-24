package isi.dan.msclientes.dao;

import static org.assertj.core.api.Assertions.assertThat;

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

import isi.dan.msclientes.model.UsuarioCliente;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class UsuarioRepositoryTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired
    private UsuarioRepository usuarioRepository;

    private UsuarioCliente crearUsuario(String nombre, String email) {
        UsuarioCliente u = new UsuarioCliente();
        u.setNombre(nombre);
        u.setEmail(email);
        u.setPassword("hashed");
        return u;
    }

    @Test
    void findByHabilitadoTrue_ShouldOnlyReturnActive() {
        UsuarioCliente activo = usuarioRepository.save(crearUsuario("Activo", "u1@mail.com"));
        UsuarioCliente borrado = usuarioRepository.save(crearUsuario("Borrado", "u2@mail.com"));
        borrado.setHabilitado(false);
        usuarioRepository.save(borrado);

        List<UsuarioCliente> result = usuarioRepository.findByHabilitadoTrue();

        assertThat(result).contains(activo).doesNotContain(borrado);
    }

    @Test
    void findByEmailAndHabilitadoTrue_ShouldReturnUsuario() {
        UsuarioCliente u = usuarioRepository.save(crearUsuario("Mail", "buscar@mail.com"));

        Optional<UsuarioCliente> result = usuarioRepository.findByEmailAndHabilitadoTrue("buscar@mail.com");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("buscar@mail.com");
    }

    @Test
    void existsByEmailAndHabilitadoTrue_ShouldReturnTrueForExisting() {
        usuarioRepository.save(crearUsuario("Existente", "existe@mail.com"));

        boolean result = usuarioRepository.existsByEmailAndHabilitadoTrue("existe@mail.com");

        assertThat(result).isTrue();
    }
}
