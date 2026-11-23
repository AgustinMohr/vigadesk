package isi.dan.msclientes.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import isi.dan.msclientes.dao.UsuarioRepository;
import isi.dan.msclientes.model.UsuarioCliente;

/**
 * Crea el primer administrador cuando la base no tiene ningun usuario.
 *
 * Las credenciales salen de variables de entorno (BOOTSTRAP_ADMIN_EMAIL y
 * BOOTSTRAP_ADMIN_PASSWORD). Si la base esta vacia y bootstrap-admin.required
 * es true (produccion) y faltan las variables, el arranque falla en lugar de
 * dejar el sistema sin acceso o con credenciales conocidas.
 * Si ya existe algun usuario no hace nada.
 */
@Component
@Order(2)
public class AdminBootstrap implements CommandLineRunner {

    static final int LONGITUD_MINIMA_PASSWORD = 12;

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;
    private final boolean required;

    public AdminBootstrap(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap-admin.email:}") String email,
            @Value("${app.bootstrap-admin.password:}") String password,
            @Value("${app.bootstrap-admin.required:false}") boolean required) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
        this.required = required;
    }

    @Override
    public void run(String... args) {
        if (usuarioRepository.count() > 0) {
            return;
        }

        boolean sinCredenciales = email == null || email.isBlank() || password == null || password.isBlank();
        if (sinCredenciales) {
            if (required) {
                throw new IllegalStateException(
                        "No hay usuarios y faltan BOOTSTRAP_ADMIN_EMAIL y BOOTSTRAP_ADMIN_PASSWORD "
                                + "para crear el primer administrador.");
            }
            log.warn("No hay usuarios. Defina BOOTSTRAP_ADMIN_EMAIL y BOOTSTRAP_ADMIN_PASSWORD "
                    + "para crear el primer administrador.");
            return;
        }

        if (!email.contains("@")) {
            throw new IllegalArgumentException("BOOTSTRAP_ADMIN_EMAIL no es un email valido.");
        }
        if (password.length() < LONGITUD_MINIMA_PASSWORD) {
            throw new IllegalArgumentException(
                    "BOOTSTRAP_ADMIN_PASSWORD debe tener al menos " + LONGITUD_MINIMA_PASSWORD + " caracteres.");
        }

        UsuarioCliente admin = new UsuarioCliente();
        admin.setNombre("Administrador");
        admin.setEmail(email.trim());
        admin.setPassword(passwordEncoder.encode(password));
        admin.setRol("ADMIN");
        usuarioRepository.save(admin);
        log.info("Se creo el administrador inicial {}", admin.getEmail());
    }
}
