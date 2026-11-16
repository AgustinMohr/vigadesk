package isi.dan.msclientes.config;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import isi.dan.msclientes.dao.ClienteRepository;
import isi.dan.msclientes.dao.ObraRepository;
import isi.dan.msclientes.dao.UsuarioRepository;
import isi.dan.msclientes.model.Cliente;
import isi.dan.msclientes.model.EstadoObra;
import isi.dan.msclientes.model.Obra;
import isi.dan.msclientes.model.UsuarioCliente;

// Inicializa datos de prueba al arrancar si la BD está vacía
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private ObraRepository obraRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (clienteRepository.count() == 0) {
            inicializarClientesYObras();
        } else {
            log.info("La base de datos ya contiene clientes. Se omite la carga de clientes y obras.");
        }

        if (usuarioRepository.count() == 0) {
            inicializarUsuarios();
        } else {
            log.info("La base de datos ya contiene usuarios. Se omite la carga de usuarios.");
        }

        log.info("Datos de prueba cargados: {} clientes, {} obras, {} usuarios",
                clienteRepository.count(), obraRepository.count(), usuarioRepository.count());
    }

    private void inicializarClientesYObras() {
        log.info("Inicializando datos de prueba...");

        Cliente c1 = new Cliente();
        c1.setNombre("Constructora del Sur S.A.");
        c1.setCorreoElectronico("contacto@constructorasur.com");
        c1.setCuit("30-12345678-9");
        c1.setMaximoDescubierto(new BigDecimal("500000"));
        c1 = clienteRepository.save(c1);

        Cliente c2 = new Cliente();
        c2.setNombre("Pablo Martinez");
        c2.setCorreoElectronico("pablo.martinez@gmail.com");
        c2.setCuit("20-23456789-0");
        c2.setMaximoDescubierto(new BigDecimal("150000"));
        c2 = clienteRepository.save(c2);

        Cliente c3 = new Cliente();
        c3.setNombre("GH Instalaciones SRL");
        c3.setCorreoElectronico("admin@ghinstalaciones.com");
        c3.setCuit("30-34567890-1");
        c3.setMaximoDescubierto(new BigDecimal("300000"));
        c3 = clienteRepository.save(c3);

        Cliente c4 = new Cliente();
        c4.setNombre("Maria Perez");
        c4.setCorreoElectronico("mariaperez@yahoo.com");
        c4.setCuit("27-45678901-2");
        c4.setMaximoDescubierto(new BigDecimal("80000"));
        c4 = clienteRepository.save(c4);

        Cliente c5 = new Cliente();
        c5.setNombre("TechBuild Argentina SA");
        c5.setCorreoElectronico("info@techbuild.com.ar");
        c5.setCuit("30-56789012-3");
        c5.setMaximoDescubierto(new BigDecimal("1000000"));
        c5 = clienteRepository.save(c5);

        Cliente c6 = new Cliente();
        c6.setNombre("Ferrum Construcciones SA");
        c6.setCorreoElectronico("obras@ferrum.com.ar");
        c6.setCuit("30-67890123-4");
        c6.setMaximoDescubierto(new BigDecimal("750000"));
        c6 = clienteRepository.save(c6);

        // c1: sin limite → 2 habilitadas + 1 finalizada
        obraRepository.save(crearObra("Av. Siempre Viva 742", -34.603f, -58.381f, new BigDecimal("250000"), c1, EstadoObra.HABILITADA));
        obraRepository.save(crearObra("Calle Falsa 123", -34.615f, -58.433f, new BigDecimal("95000"), c1, EstadoObra.HABILITADA));
        obraRepository.save(crearObra("Av. Corrientes 2000", -34.604f, -58.374f, new BigDecimal("400000"), c1, EstadoObra.FINALIZADA));

        // c2: max=1 → 1 habilitada + 1 pendiente (tope alcanzado)
        obraRepository.save(crearObra("San Martin 456", -34.598f, -58.420f, new BigDecimal("180000"), c2, EstadoObra.HABILITADA));
        obraRepository.save(crearObra("Lavalle 789", -34.601f, -58.377f, new BigDecimal("75000"), c2, EstadoObra.PENDIENTE));

        // c3: max=2 → 2 habilitadas + 1 pendiente (tope alcanzado)
        obraRepository.save(crearObra("Belgrano 789", -34.620f, -58.450f, new BigDecimal("120000"), c3, EstadoObra.HABILITADA));
        obraRepository.save(crearObra("Mitre 101", -34.609f, -58.393f, new BigDecimal("220000"), c3, EstadoObra.HABILITADA));
        obraRepository.save(crearObra("Saavedra 222", -34.602f, -58.369f, new BigDecimal("90000"), c3, EstadoObra.PENDIENTE));

        // c4: max=1 → ninguna habilitada aun, 1 pendiente (se creo manual como pendiente)
        obraRepository.save(crearObra("Independencia 333", -34.617f, -58.431f, new BigDecimal("110000"), c4, EstadoObra.PENDIENTE));

        // c5: max=0 → no puede tener habilitadas, 1 pendiente
        obraRepository.save(crearObra("Colon 1011", -34.605f, -58.395f, new BigDecimal("500000"), c5, EstadoObra.PENDIENTE));

        // c6: max=2 → 2 finalizadas (sin activas, cupo disponible)
        obraRepository.save(crearObra("9 de Julio 1500", -34.607f, -58.382f, new BigDecimal("310000"), c6, EstadoObra.FINALIZADA));
        obraRepository.save(crearObra("Pueyrredon 2500", -34.612f, -58.409f, new BigDecimal("280000"), c6, EstadoObra.FINALIZADA));

        // Obras sin cliente
        obraRepository.save(crearObra("Diagonal Norte 800", -34.608f, -58.385f, new BigDecimal("160000"), null, null));
        obraRepository.save(crearObra("Alem 500", -34.614f, -58.376f, new BigDecimal("200000"), null, null));
    }

    private void inicializarUsuarios() {
        log.info("Inicializando usuarios de prueba...");

        Cliente c1 = clienteRepository.findByCorreoElectronico("contacto@constructorasur.com").orElse(null);
        Cliente c3 = clienteRepository.findByCorreoElectronico("admin@ghinstalaciones.com").orElse(null);
        Cliente c6 = clienteRepository.findByCorreoElectronico("obras@ferrum.com.ar").orElse(null);

        // Usuarios de clientes: cada cliente puede tener uno o más usuarios que hacen pedidos en su nombre
        usuarioRepository.save(crearUsuario("Juan", "Perez", "juan@constructorasur.com", "30111222", "juan123", c1));
        usuarioRepository.save(crearUsuario("Maria", "Lopez", "maria@constructorasur.com", "30222333", "maria123", c1));
        usuarioRepository.save(crearUsuario("Jose", "Gomez", "jose@ghinstalaciones.com", "30333444", "jose123", c3));
        usuarioRepository.save(crearUsuario("Ana", "Ruiz", "ana@ferrum.com.ar", "30444555", "ana123", c6));

        // Usuario de sistema con acceso total.
        UsuarioCliente admin = crearUsuario("Administrador", null, "admin@example.com", null, "admin123", null);
        admin.setRol("ADMIN");
        usuarioRepository.save(admin);
    }

    private Obra crearObra(String direccion, float lat, float lng, BigDecimal presupuesto, Cliente cliente, EstadoObra estado) {
        Obra o = new Obra();
        o.setDireccion(direccion);
        o.setLat(lat);
        o.setLng(lng);
        o.setPresupuesto(presupuesto);
        o.setCliente(cliente);
        o.setEstado(estado);
        return o;
    }

    private UsuarioCliente crearUsuario(String nombre, String apellido, String email, String dni, String password, Cliente cliente) {
        UsuarioCliente u = new UsuarioCliente();
        u.setNombre(nombre);
        u.setApellido(apellido);
        u.setEmail(email);
        u.setDni(dni);
        u.setPassword(passwordEncoder.encode(password));
        u.setCliente(cliente);
        return u;
    }
}
