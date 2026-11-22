package isi.dan.msclientes.servicios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import isi.dan.msclientes.dao.UsuarioRepository;
import isi.dan.msclientes.model.UsuarioCliente;

/**
 * El login no debe distinguir por tiempo "usuario inexistente" de "contrasena incorrecta". Se usa BCrypt real
 * y se compara la mediana de varias corridas con una tolerancia amplia (los tiempos varian con la carga de la
 * maquina; lo que se descarta es la diferencia de un orden de magnitud entre hacer BCrypt o no hacerlo).
 */
class UsuarioServiceTiemposTest {

    private static final int CORRIDAS = 7;

    private long medianaNanos(Runnable accion) {
        long[] tiempos = new long[CORRIDAS];
        for (int i = 0; i < CORRIDAS; i++) {
            long inicio = System.nanoTime();
            accion.run();
            tiempos[i] = System.nanoTime() - inicio;
        }
        Arrays.sort(tiempos);
        return tiempos[CORRIDAS / 2];
    }

    @Test
    void loginDeUsuarioInexistente_ShouldTakeSimilarTimeAsWrongPassword() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        UsuarioRepository repo = mock(UsuarioRepository.class);
        UsuarioCliente existente = new UsuarioCliente();
        existente.setEmail("juan@mail.com");
        existente.setPassword(encoder.encode("la-contrasena-real-123"));
        when(repo.findByEmailAndHabilitadoTrue("juan@mail.com")).thenReturn(Optional.of(existente));
        when(repo.findByEmailAndHabilitadoTrue("nadie@mail.com")).thenReturn(Optional.empty());

        UsuarioService service = new UsuarioService();
        ReflectionTestUtils.setField(service, "usuarioRepository", repo);
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
        // sin bloqueo durante la medicion: el limite se prueba aparte
        ReflectionTestUtils.setField(service, "maxFailures", 1000);

        service.validarLogin("nadie@mail.com", "x"); // calienta JIT y crea el hash de relleno

        long conUsuarioIncorrecto = medianaNanos(() -> service.validarLogin("nadie@mail.com", "contrasena-mala-1"));
        long conPasswordIncorrecta = medianaNanos(() -> service.validarLogin("juan@mail.com", "contrasena-mala-1"));

        long mayor = Math.max(conUsuarioIncorrecto, conPasswordIncorrecta);
        long diferencia = Math.abs(conUsuarioIncorrecto - conPasswordIncorrecta);
        assertThat(conUsuarioIncorrecto).as("el usuario inexistente tambien paga BCrypt").isGreaterThan(10_000_000L);
        assertThat(diferencia).as("diferencia de tiempos (ns)").isLessThan((long) (mayor * 0.6));
    }
}
