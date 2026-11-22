package isi.dan.msclientes.servicios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import dan.common.error.DemasiadosIntentosException;
import isi.dan.msclientes.dao.UsuarioRepository;
import isi.dan.msclientes.model.UsuarioCliente;

/** R2.10: politica de contrasenas, limite de intentos por usuario y tiempo constante del login. */
@ExtendWith(MockitoExtension.class)
class UsuarioServiceSeguridadTest {

    private static final String EMAIL = "juan@constructorasur.com";

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UsuarioService usuarioService;

    private UsuarioCliente usuario;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(usuarioService, "maxFailures", 3);
        ReflectionTestUtils.setField(usuarioService, "windowSeconds", 60L);
        usuario = new UsuarioCliente();
        usuario.setId(1);
        usuario.setEmail(EMAIL);
        usuario.setPassword("hash-guardado");
    }

    private void fallar(int veces, String email) {
        for (int i = 0; i < veces; i++) {
            assertThat(usuarioService.validarLogin(email, "incorrecta")).isEmpty();
        }
    }

    // ---- politica de contrasenas ----

    @Test
    void save_PasswordCorta_ShouldBeRejectedAndNotSaved() {
        usuario.setPassword("corta123");
        when(usuarioRepository.existsByEmailAndHabilitadoTrue(EMAIL)).thenReturn(false);

        assertThatThrownBy(() -> usuarioService.save(usuario))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("al menos 12 caracteres");

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void save_PasswordDeLongitudMinima_ShouldBeAccepted() {
        usuario.setPassword("123456789012");
        when(usuarioRepository.existsByEmailAndHabilitadoTrue(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode("123456789012")).thenReturn("hash");
        when(usuarioRepository.save(any(UsuarioCliente.class))).thenReturn(usuario);

        assertThat(usuarioService.save(usuario)).isNotNull();
    }

    @Test
    void save_PasswordNula_ShouldBeRejected() {
        usuario.setPassword(null);
        when(usuarioRepository.existsByEmailAndHabilitadoTrue(EMAIL)).thenReturn(false);

        assertThatThrownBy(() -> usuarioService.save(usuario)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void update_PasswordNuevaCorta_ShouldBeRejected() {
        UsuarioCliente nuevo = new UsuarioCliente();
        nuevo.setPassword("corta");

        assertThatThrownBy(() -> usuarioService.update(usuario, nuevo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("al menos 12 caracteres");

        assertThat(usuario.getPassword()).isEqualTo("hash-guardado");
        verify(usuarioRepository, never()).save(any());
    }

    // ---- limite de intentos ----

    @Test
    void validarLogin_TrasMaxFallos_ShouldThrowTooManyRequestsWithoutCheckingThePassword() {
        when(usuarioRepository.findByEmailAndHabilitadoTrue(EMAIL)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(anyString(), eq("hash-guardado"))).thenReturn(false);
        fallar(3, EMAIL);

        assertThatThrownBy(() -> usuarioService.validarLogin(EMAIL, "incorrecta"))
                .isInstanceOf(DemasiadosIntentosException.class)
                .hasMessageContaining("Demasiados intentos");

        // el 4to intento no llega a comparar: 3 comparaciones en total
        verify(passwordEncoder, times(3)).matches(anyString(), eq("hash-guardado"));
    }

    @Test
    void validarLogin_UsuarioBloqueado_ShouldRejectEvenWithTheCorrectPassword() {
        when(usuarioRepository.findByEmailAndHabilitadoTrue(EMAIL)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("incorrecta", "hash-guardado")).thenReturn(false);
        fallar(3, EMAIL);

        assertThatThrownBy(() -> usuarioService.validarLogin(EMAIL, "la-correcta-123"))
                .isInstanceOf(DemasiadosIntentosException.class);
    }

    @Test
    void validarLogin_EmailEnOtraCapitalizacion_ShouldCountAgainstTheSameKey() {
        when(usuarioRepository.findByEmailAndHabilitadoTrue(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hash-relleno");
        fallar(1, "Juan@ConstructoraSur.com");
        fallar(1, " JUAN@constructorasur.com ");
        fallar(1, EMAIL);

        assertThatThrownBy(() -> usuarioService.validarLogin(EMAIL, "x"))
                .isInstanceOf(DemasiadosIntentosException.class);
    }

    @Test
    void validarLogin_LoginCorrecto_ShouldResetTheFailureCount() {
        when(usuarioRepository.findByEmailAndHabilitadoTrue(EMAIL)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("incorrecta", "hash-guardado")).thenReturn(false);
        when(passwordEncoder.matches("correcta-12345", "hash-guardado")).thenReturn(true);
        fallar(2, EMAIL);

        assertThat(usuarioService.validarLogin(EMAIL, "correcta-12345")).isPresent();
        fallar(2, EMAIL); // arranca de cero: 2 fallos mas siguen sin bloquear

        assertThat(usuarioService.validarLogin(EMAIL, "correcta-12345")).isPresent();
    }

    @Test
    void validarLogin_UsuariosInexistentes_ShouldAlsoBeLimitedWithoutRevealingThem() {
        when(usuarioRepository.findByEmailAndHabilitadoTrue("nadie@mail.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hash-relleno");
        fallar(3, "nadie@mail.com");

        assertThatThrownBy(() -> usuarioService.validarLogin("nadie@mail.com", "x"))
                .isInstanceOf(DemasiadosIntentosException.class);
    }

    @Test
    void validarLogin_OtroUsuario_ShouldNotBeAffectedByTheBlock() {
        when(usuarioRepository.findByEmailAndHabilitadoTrue(EMAIL)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.findByEmailAndHabilitadoTrue("otro@mail.com")).thenReturn(Optional.empty());
        when(passwordEncoder.matches(anyString(), eq("hash-guardado"))).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hash-relleno");
        fallar(3, EMAIL);

        assertThat(usuarioService.validarLogin("otro@mail.com", "x")).isEmpty();
    }

    // ---- tiempo constante ----

    @Test
    void validarLogin_UsuarioInexistente_ShouldStillCompareAgainstAFillerHash() {
        when(usuarioRepository.findByEmailAndHabilitadoTrue("nadie@mail.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hash-relleno");

        assertThat(usuarioService.validarLogin("nadie@mail.com", "cualquiera")).isEmpty();

        // misma cantidad de comparaciones bcrypt que con un usuario existente
        verify(passwordEncoder).matches("cualquiera", "hash-relleno");
    }

    @Test
    void validarLogin_HashDeRelleno_ShouldBeCreatedOnlyOnce() {
        when(usuarioRepository.findByEmailAndHabilitadoTrue(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hash-relleno");

        usuarioService.validarLogin("a@mail.com", "x");
        usuarioService.validarLogin("b@mail.com", "x");

        verify(passwordEncoder, times(1)).encode(anyString());
    }

    @Test
    void validarLogin_EmailNulo_ShouldNotBreak() {
        when(usuarioRepository.findByEmailAndHabilitadoTrue(null)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hash-relleno");

        assertThat(usuarioService.validarLogin(null, "x")).isEmpty();
    }
}
