package isi.dan.msclientes.config;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import isi.dan.msclientes.dao.ClienteRepository;
import isi.dan.msclientes.dao.ObraRepository;
import isi.dan.msclientes.model.Cliente;
import isi.dan.msclientes.model.Obra;

// Inicializa datos de prueba al arrancar si la BD está vacía
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private ObraRepository obraRepository;

    @Override
    public void run(String... args) {
        if (clienteRepository.count() > 0) {
            log.info("La base de datos ya contiene datos. Se omite la inicializacion.");
            return;
        }

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

        obraRepository.save(crearObra("Av. Siempre Viva 742", -34.603f, -58.381f, new BigDecimal("250000"), c1));
        obraRepository.save(crearObra("Calle Falsa 123", -34.615f, -58.433f, new BigDecimal("95000"), c1));
        obraRepository.save(crearObra("San Martin 456", -34.598f, -58.420f, new BigDecimal("180000"), c2));
        obraRepository.save(crearObra("Belgrano 789", -34.620f, -58.450f, new BigDecimal("120000"), c3));
        obraRepository.save(crearObra("Colon 1011", -34.605f, -58.395f, new BigDecimal("500000"), c5));

        log.info("Datos de prueba cargados: {} clientes, {} obras",
                clienteRepository.count(), obraRepository.count());
    }

    private Obra crearObra(String direccion, float lat, float lng, BigDecimal presupuesto, Cliente cliente) {
        Obra o = new Obra();
        o.setDireccion(direccion);
        o.setLat(lat);
        o.setLng(lng);
        o.setPresupuesto(presupuesto);
        o.setCliente(cliente);
        return o;
    }
}
