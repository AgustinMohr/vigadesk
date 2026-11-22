package isi.dan.msclientes.servicios;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import dan.common.error.DemasiadosIntentosException;
import dan.common.security.FailedAttemptLimiter;
import isi.dan.msclientes.dao.UsuarioRepository;
import isi.dan.msclientes.model.UsuarioCliente;

@Service
public class UsuarioService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /** Misma longitud minima que la contrasena del administrador inicial (AdminBootstrap). */
    static final int LONGITUD_MINIMA_PASSWORD = 12;

    @Value("${login.max-failures:5}")
    private int maxFailures = 5;

    @Value("${login.window-seconds:60}")
    private long windowSeconds = 60;

    private FailedAttemptLimiter limiter;
    private String hashDeRelleno;

    public List<UsuarioCliente> findAll() {
        return usuarioRepository.findByHabilitadoTrue();
    }

    public Optional<UsuarioCliente> findById(Integer id) {
        return usuarioRepository.findByIdAndHabilitadoTrue(id);
    }

    public UsuarioCliente save(UsuarioCliente usuario) {
        if (usuarioRepository.existsByEmailAndHabilitadoTrue(usuario.getEmail())) {
            throw new IllegalArgumentException("Ya existe un usuario habilitado con el email " + usuario.getEmail());
        }
        validarPassword(usuario.getPassword());
        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
        return usuarioRepository.save(usuario);
    }

    public UsuarioCliente update(UsuarioCliente existente, UsuarioCliente nuevo) {
        if (nuevo.getNombre() != null) {
            existente.setNombre(nuevo.getNombre());
        }
        if (nuevo.getApellido() != null) {
            existente.setApellido(nuevo.getApellido());
        }
        if (nuevo.getDni() != null) {
            existente.setDni(nuevo.getDni());
        }
        if (nuevo.getEmail() != null) {
            usuarioRepository.findByEmailAndHabilitadoTrue(nuevo.getEmail())
                .filter(otro -> !otro.getId().equals(existente.getId()))
                .ifPresent(otro -> {
                    throw new IllegalArgumentException("Ya existe un usuario habilitado con el email " + nuevo.getEmail());
                });
            existente.setEmail(nuevo.getEmail());
        }
        if (nuevo.getPassword() != null && !nuevo.getPassword().isBlank()) {
            validarPassword(nuevo.getPassword());
            existente.setPassword(passwordEncoder.encode(nuevo.getPassword()));
        }
        if (nuevo.getCliente() != null && nuevo.getCliente().getId() != null) {
            existente.setCliente(nuevo.getCliente());
        }
        return usuarioRepository.save(existente);
    }

    public void deleteById(Integer id) {
        usuarioRepository.findByIdAndHabilitadoTrue(id).ifPresent(usuario -> {
            usuario.setHabilitado(false);
            usuario.setFechaBorrado(Instant.now());
            usuarioRepository.save(usuario);
        });
    }

    /**
     * Valida credenciales. Frena la fuerza bruta (mas de maxFailures fallos por usuario en la ventana: 429)
     * y responde en tiempo parecido exista o no el usuario: sin usuario se compara contra un hash de relleno.
     * El limite se lleva por email normalizado, exista o no la cuenta, asi tampoco revela cuales existen.
     */
    public Optional<UsuarioCliente> validarLogin(String email, String rawPassword) {
        String clave = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        long espera = limiter().retryAfterSeconds(clave);
        if (espera > 0) {
            throw new DemasiadosIntentosException(
                    "Demasiados intentos fallidos. Intenta de nuevo en " + espera + " segundos");
        }

        Optional<UsuarioCliente> encontrado = usuarioRepository.findByEmailAndHabilitadoTrue(email);
        boolean correcto;
        if (encontrado.isPresent()) {
            correcto = passwordEncoder.matches(rawPassword, encontrado.get().getPassword());
        } else {
            passwordEncoder.matches(rawPassword, hashDeRelleno());
            correcto = false;
        }

        if (!correcto) {
            limiter().recordFailure(clave);
            return Optional.empty();
        }
        limiter().reset(clave);
        return encontrado;
    }

    /** Politica de contrasenas: longitud minima. */
    static void validarPassword(String password) {
        if (password == null || password.length() < LONGITUD_MINIMA_PASSWORD) {
            throw new IllegalArgumentException(
                    "La contrasena debe tener al menos " + LONGITUD_MINIMA_PASSWORD + " caracteres");
        }
    }

    private synchronized FailedAttemptLimiter limiter() {
        if (limiter == null) {
            limiter = new FailedAttemptLimiter(maxFailures, Duration.ofSeconds(windowSeconds));
        }
        return limiter;
    }

    private synchronized String hashDeRelleno() {
        if (hashDeRelleno == null) {
            hashDeRelleno = passwordEncoder.encode(UUID.randomUUID().toString());
        }
        return hashDeRelleno;
    }
}
