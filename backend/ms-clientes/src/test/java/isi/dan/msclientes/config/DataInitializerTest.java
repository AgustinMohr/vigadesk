package isi.dan.msclientes.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import isi.dan.msclientes.dao.ClienteRepository;
import isi.dan.msclientes.dao.ObraRepository;
import isi.dan.msclientes.dao.UsuarioRepository;
import isi.dan.msclientes.model.Cliente;
import isi.dan.msclientes.model.Obra;
import isi.dan.msclientes.model.UsuarioCliente;

/** R1.3: los datos de ejemplo solo existen con app.seed.enabled=true y nunca guardan una contrasena en claro. */
class DataInitializerTest {

    private final ClienteRepository clientes = mock(ClienteRepository.class);
    private final ObraRepository obras = mock(ObraRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

    private DataInitializer inicializador() {
        when(clientes.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));
        return new DataInitializer(clientes, obras, usuarios, encoder);
    }

    // ---- condicion de activacion ----

    private ApplicationContextRunner contexto() {
        return new ApplicationContextRunner()
                .withBean(ClienteRepository.class, () -> clientes)
                .withBean(ObraRepository.class, () -> obras)
                .withBean(UsuarioRepository.class, () -> usuarios)
                .withBean(PasswordEncoder.class, () -> encoder)
                .withUserConfiguration(DataInitializer.class);
    }

    @Test
    void sinLaPropiedad_ElInicializadorNoExiste() {
        contexto().run(ctx -> assertThat(ctx).doesNotHaveBean(DataInitializer.class));
    }

    @Test
    void conSeedEnFalse_ElInicializadorNoExiste() {
        contexto().withPropertyValues("app.seed.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(DataInitializer.class));
    }

    @Test
    void conSeedEnTrue_ElInicializadorExiste() {
        when(clientes.count()).thenReturn(1L);
        when(usuarios.count()).thenReturn(1L);

        contexto().withPropertyValues("app.seed.enabled=true")
                .run(ctx -> assertThat(ctx).hasSingleBean(DataInitializer.class));
    }

    // ---- comportamiento ----

    @Test
    void baseVacia_ShouldCreateClientesObrasYUsuarios() {
        when(clientes.count()).thenReturn(0L);
        when(usuarios.count()).thenReturn(0L);
        when(clientes.findByCorreoElectronico(any())).thenReturn(Optional.of(new Cliente()));

        inicializador().run();

        verify(clientes, org.mockito.Mockito.times(6)).save(any(Cliente.class));
        verify(obras, org.mockito.Mockito.atLeast(10)).save(any(Obra.class));
        ArgumentCaptor<UsuarioCliente> captor = ArgumentCaptor.forClass(UsuarioCliente.class);
        verify(usuarios, org.mockito.Mockito.times(5)).save(captor.capture());
        List<UsuarioCliente> creados = captor.getAllValues();
        assertThat(creados).filteredOn(u -> "ADMIN".equals(u.getRol())).hasSize(1);
    }

    @Test
    void lasContrasenasSeGuardanEnBcrypt() {
        when(clientes.count()).thenReturn(1L); // solo usuarios
        when(usuarios.count()).thenReturn(0L);
        when(clientes.findByCorreoElectronico(any())).thenReturn(Optional.empty());

        inicializador().run();

        ArgumentCaptor<UsuarioCliente> captor = ArgumentCaptor.forClass(UsuarioCliente.class);
        verify(usuarios, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(u -> {
            assertThat(u.getPassword()).startsWith("$2");
            assertThat(u.getPassword()).isNotEqualTo("admin123").isNotEqualTo("juan123");
        });
        UsuarioCliente admin = captor.getAllValues().stream().filter(u -> "ADMIN".equals(u.getRol())).findFirst().orElseThrow();
        assertThat(encoder.matches("admin123", admin.getPassword())).isTrue();
    }

    @Test
    void baseConDatos_NoCreaNada() {
        when(clientes.count()).thenReturn(3L);
        when(usuarios.count()).thenReturn(2L);

        inicializador().run();

        verify(clientes, never()).save(any(Cliente.class));
        verify(obras, never()).save(any(Obra.class));
        verify(usuarios, never()).save(any(UsuarioCliente.class));
    }
}
