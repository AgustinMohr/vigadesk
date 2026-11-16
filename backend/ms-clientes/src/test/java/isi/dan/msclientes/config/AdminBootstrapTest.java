package isi.dan.msclientes.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import isi.dan.msclientes.dao.ClienteRepository;
import isi.dan.msclientes.dao.ObraRepository;
import isi.dan.msclientes.dao.UsuarioRepository;
import isi.dan.msclientes.model.UsuarioCliente;

class AdminBootstrapTest {

    private UsuarioRepository usuarioRepository;
    private PasswordEncoder passwordEncoder;
    private AdminBootstrap bootstrap;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        bootstrap = new AdminBootstrap();
        ReflectionTestUtils.setField(bootstrap, "usuarioRepository", usuarioRepository);
        ReflectionTestUtils.setField(bootstrap, "passwordEncoder", passwordEncoder);
    }

    private void configurar(String email, String password, boolean required) {
        ReflectionTestUtils.setField(bootstrap, "email", email);
        ReflectionTestUtils.setField(bootstrap, "password", password);
        ReflectionTestUtils.setField(bootstrap, "required", required);
    }

    @Test
    void run_WhenUsersExist_ShouldDoNothing() {
        when(usuarioRepository.count()).thenReturn(3L);
        configurar("admin@empresa.com", "una-clave-larga-123", true);

        bootstrap.run();

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void run_WhenEmptyAndCredentialsProvided_ShouldCreateAdminWithHashedPassword() {
        when(usuarioRepository.count()).thenReturn(0L);
        configurar("admin@empresa.com", "una-clave-larga-123", true);

        bootstrap.run();

        ArgumentCaptor<UsuarioCliente> captor = ArgumentCaptor.forClass(UsuarioCliente.class);
        verify(usuarioRepository).save(captor.capture());
        UsuarioCliente creado = captor.getValue();
        assertThat(creado.getRol()).isEqualTo("ADMIN");
        assertThat(creado.getEmail()).isEqualTo("admin@empresa.com");
        assertThat(creado.getPassword()).isNotEqualTo("una-clave-larga-123");
        assertThat(passwordEncoder.matches("una-clave-larga-123", creado.getPassword())).isTrue();
    }

    @Test
    void run_WhenEmptyWithoutCredentialsAndRequired_ShouldFailStartup() {
        when(usuarioRepository.count()).thenReturn(0L);
        configurar("", "", true);

        assertThatThrownBy(() -> bootstrap.run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOTSTRAP_ADMIN_EMAIL");
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void run_WhenEmptyWithoutCredentialsAndNotRequired_ShouldOnlyWarn() {
        when(usuarioRepository.count()).thenReturn(0L);
        configurar("", "", false);

        bootstrap.run();

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void run_WhenPasswordTooShort_ShouldThrow() {
        when(usuarioRepository.count()).thenReturn(0L);
        configurar("admin@empresa.com", "corta123", true);

        assertThatThrownBy(() -> bootstrap.run())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("12");
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void run_WhenEmailInvalid_ShouldThrow() {
        when(usuarioRepository.count()).thenReturn(0L);
        configurar("sin-arroba", "una-clave-larga-123", true);

        assertThatThrownBy(() -> bootstrap.run())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
        verify(usuarioRepository, never()).save(any());
    }

    // --- El seed de ejemplo solo existe si app.seed.enabled=true ---

    private ApplicationContextRunner contextoConSeed() {
        return new ApplicationContextRunner()
                .withUserConfiguration(DataInitializer.class)
                .withBean(ClienteRepository.class, () -> mock(ClienteRepository.class))
                .withBean(ObraRepository.class, () -> mock(ObraRepository.class))
                .withBean(UsuarioRepository.class, () -> mock(UsuarioRepository.class))
                .withBean(PasswordEncoder.class, BCryptPasswordEncoder::new);
    }

    @Test
    void dataInitializer_ByDefault_ShouldNotBeCreated() {
        contextoConSeed().run(ctx -> assertThat(ctx).doesNotHaveBean(DataInitializer.class));
    }

    @Test
    void dataInitializer_WhenSeedDisabled_ShouldNotBeCreated() {
        contextoConSeed().withPropertyValues("app.seed.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(DataInitializer.class));
    }

    @Test
    void dataInitializer_WhenSeedEnabled_ShouldBeCreated() {
        contextoConSeed().withPropertyValues("app.seed.enabled=true")
                .run(ctx -> assertThat(ctx).hasSingleBean(DataInitializer.class));
    }
}
