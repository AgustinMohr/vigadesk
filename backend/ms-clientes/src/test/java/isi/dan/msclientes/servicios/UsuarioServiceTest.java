package isi.dan.msclientes.servicios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import isi.dan.msclientes.dao.UsuarioRepository;
import isi.dan.msclientes.model.Cliente;
import isi.dan.msclientes.model.UsuarioCliente;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UsuarioService usuarioService;

    private UsuarioCliente usuario;
    private Cliente cliente;

    @BeforeEach
    void setUp() {
        cliente = new Cliente();
        cliente.setId(1);
        cliente.setNombre("Constructora del Sur S.A.");

        usuario = new UsuarioCliente();
        usuario.setId(1);
        usuario.setNombre("Juan Perez");
        usuario.setEmail("juan@constructorasur.com");
        usuario.setPassword("juan123");
        usuario.setCliente(cliente);
    }

    @Test
    void findAll_ShouldReturnAllUsuarios() {
        when(usuarioRepository.findByHabilitadoTrue()).thenReturn(Collections.singletonList(usuario));

        List<UsuarioCliente> result = usuarioService.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmail()).isEqualTo("juan@constructorasur.com");
    }

    @Test
    void findById_WhenExists_ShouldReturnUsuario() {
        when(usuarioRepository.findByIdAndHabilitadoTrue(1)).thenReturn(Optional.of(usuario));

        Optional<UsuarioCliente> result = usuarioService.findById(1);

        assertThat(result).isPresent();
        assertThat(result.get().getNombre()).isEqualTo("Juan Perez");
    }

    @Test
    void findById_WhenNotExists_ShouldReturnEmpty() {
        when(usuarioRepository.findByIdAndHabilitadoTrue(99)).thenReturn(Optional.empty());

        Optional<UsuarioCliente> result = usuarioService.findById(99);

        assertThat(result).isEmpty();
    }

    @Test
    void save_WhenEmailLibre_ShouldReturnSavedUsuario() {
        when(usuarioRepository.existsByEmailAndHabilitadoTrue("juan@constructorasur.com")).thenReturn(false);
        when(passwordEncoder.encode("juan123")).thenReturn("hashed123");
        when(usuarioRepository.save(any(UsuarioCliente.class))).thenReturn(usuario);

        UsuarioCliente result = usuarioService.save(usuario);

        assertThat(result).isNotNull();
        assertThat(result.getNombre()).isEqualTo("Juan Perez");
    }

    @Test
    void save_WhenEmailDuplicado_ShouldThrowIllegalArgumentException() {
        when(usuarioRepository.existsByEmailAndHabilitadoTrue("juan@constructorasur.com")).thenReturn(true);

        assertThatThrownBy(() -> usuarioService.save(usuario))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("juan@constructorasur.com");
    }

    @Test
    void update_ShouldPreservePasswordWhenBlank() {
        when(usuarioRepository.save(any(UsuarioCliente.class))).thenReturn(usuario);

        UsuarioCliente nuevo = new UsuarioCliente();
        nuevo.setNombre("Juan Carlos Perez");
        nuevo.setPassword("");

        UsuarioCliente result = usuarioService.update(usuario, nuevo);

        assertThat(result.getNombre()).isEqualTo("Juan Carlos Perez");
        assertThat(result.getPassword()).isEqualTo("juan123");
    }

    @Test
    void update_ShouldEncodeNewPasswordWhenProvided() {
        when(usuarioRepository.save(any(UsuarioCliente.class))).thenReturn(usuario);
        when(passwordEncoder.encode("nuevo-pass")).thenReturn("hashed-nuevo");

        UsuarioCliente nuevo = new UsuarioCliente();
        nuevo.setPassword("nuevo-pass");

        UsuarioCliente result = usuarioService.update(usuario, nuevo);

        assertThat(result.getPassword()).isEqualTo("hashed-nuevo");
    }

    @Test
    void update_ShouldUpdateApellidoYDni() {
        when(usuarioRepository.save(any(UsuarioCliente.class))).thenAnswer(i -> i.getArgument(0));

        UsuarioCliente nuevo = new UsuarioCliente();
        nuevo.setApellido("Perez");
        nuevo.setDni("30111222");

        UsuarioCliente result = usuarioService.update(usuario, nuevo);

        assertThat(result.getApellido()).isEqualTo("Perez");
        assertThat(result.getDni()).isEqualTo("30111222");
    }

    @Test
    void deleteById_ShouldSoftDeleteUsuario() {
        when(usuarioRepository.findByIdAndHabilitadoTrue(1)).thenReturn(Optional.of(usuario));

        usuarioService.deleteById(1);

        assertThat(usuario.isHabilitado()).isFalse();
        assertThat(usuario.getFechaBorrado()).isNotNull();
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void validarLogin_WhenCredencialesCorrectas_ShouldReturnUsuario() {
        when(usuarioRepository.findByEmailAndHabilitadoTrue("juan@constructorasur.com"))
                .thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("juan123", "juan123")).thenReturn(true);

        Optional<UsuarioCliente> result = usuarioService.validarLogin("juan@constructorasur.com", "juan123");

        assertThat(result).isPresent();
        assertThat(result.get().getCliente().getId()).isEqualTo(1);
    }

    @Test
    void validarLogin_WhenPasswordIncorrecta_ShouldReturnEmpty() {
        when(usuarioRepository.findByEmailAndHabilitadoTrue("juan@constructorasur.com"))
                .thenReturn(Optional.of(usuario));

        Optional<UsuarioCliente> result = usuarioService.validarLogin("juan@constructorasur.com", "incorrecta");

        assertThat(result).isEmpty();
    }

    @Test
    void validarLogin_WhenUsuarioNoExiste_ShouldReturnEmpty() {
        when(usuarioRepository.findByEmailAndHabilitadoTrue("nadie@mail.com")).thenReturn(Optional.empty());

        Optional<UsuarioCliente> result = usuarioService.validarLogin("nadie@mail.com", "x");

        assertThat(result).isEmpty();
    }
}
