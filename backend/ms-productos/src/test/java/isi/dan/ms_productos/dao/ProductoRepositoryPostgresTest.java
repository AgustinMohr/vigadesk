package isi.dan.ms_productos.dao;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import isi.dan.ms_productos.modelo.Producto;

/**
 * Prueba contra PostgreSQL real: las migraciones Flyway V1 y V2 corren sobre una
 * base vacia y Hibernate valida el esquema resultante (ddl-auto=validate).
 * Tambien prueba descontarStock/reponerStock con hilos concurrentes.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProductoRepositoryPostgresTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ProductoRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Producto crearProducto(String codigo, String stock) {
        Producto p = new Producto();
        p.setCodigo(codigo);
        p.setNombre("Producto " + codigo);
        p.setPrecio(BigDecimal.valueOf(100));
        p.setStockActual(new BigDecimal(stock));
        return repository.save(p);
    }

    private int enTransaccion(IntSupplier operacion) {
        return new TransactionTemplate(transactionManager).execute(status -> operacion.getAsInt());
    }

    @Test
    void migracionesFlywayYValidacionDeEsquema_ShouldPermitirGuardarYLeer() {
        Producto guardado = crearProducto("MIG-1", "5.000");

        assertThat(repository.findByIdAndHabilitadoTrue(guardado.getId())).isPresent();
        assertThat(repository.findFirstByCodigo("MIG-1")).isPresent();
    }

    @Test
    void descontarStock_ConcurrentThreads_ShouldNeverOversell() throws Exception {
        int stockInicial = 10;
        int hilos = 40;
        Producto p = crearProducto("CONC-1", String.valueOf(stockInicial));

        List<Integer> resultados = ejecutarEnParalelo(hilos,
                () -> enTransaccion(() -> repository.descontarStock(
                        p.getId(), BigDecimal.ONE, "test", Instant.now())));

        long exitosos = resultados.stream().filter(r -> r == 1).count();
        BigDecimal stockFinal = repository.findById(p.getId()).orElseThrow().getStockActual();

        assertThat(exitosos).isEqualTo(stockInicial);
        assertThat(stockFinal).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void reponerStock_ConcurrentThreads_ShouldNotLoseUpdates() throws Exception {
        int hilos = 40;
        Producto p = crearProducto("CONC-2", "0");

        ejecutarEnParalelo(hilos,
                () -> enTransaccion(() -> repository.reponerStock(
                        p.getId(), BigDecimal.ONE, "test", Instant.now())));

        BigDecimal stockFinal = repository.findById(p.getId()).orElseThrow().getStockActual();
        assertThat(stockFinal).isEqualByComparingTo(BigDecimal.valueOf(hilos));
    }

    @Test
    void descontarYReponer_Mixed_ShouldKeepConsistentStock() throws Exception {
        int inicial = 20;
        int pares = 20;
        Producto p = crearProducto("CONC-3", String.valueOf(inicial));

        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch inicio = new CountDownLatch(1);
        List<Future<Integer>> descuentos = new ArrayList<>();
        List<Future<Integer>> reposiciones = new ArrayList<>();
        for (int i = 0; i < pares; i++) {
            descuentos.add(pool.submit(() -> {
                inicio.await();
                return enTransaccion(() -> repository.descontarStock(
                        p.getId(), BigDecimal.ONE, "test", Instant.now()));
            }));
            reposiciones.add(pool.submit(() -> {
                inicio.await();
                return enTransaccion(() -> repository.reponerStock(
                        p.getId(), BigDecimal.ONE, "test", Instant.now()));
            }));
        }
        inicio.countDown();
        int descontados = 0;
        for (Future<Integer> f : descuentos) {
            descontados += f.get(30, TimeUnit.SECONDS);
        }
        for (Future<Integer> f : reposiciones) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        BigDecimal esperado = BigDecimal.valueOf(inicial + pares - descontados);
        assertThat(repository.findById(p.getId()).orElseThrow().getStockActual())
                .isEqualByComparingTo(esperado);
    }

    private List<Integer> ejecutarEnParalelo(int hilos, Callable<Integer> tarea) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch inicio = new CountDownLatch(1);
        List<Future<Integer>> futuros = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            futuros.add(pool.submit(() -> {
                inicio.await();
                return tarea.call();
            }));
        }
        inicio.countDown();
        List<Integer> resultados = new ArrayList<>();
        for (Future<Integer> f : futuros) {
            resultados.add(f.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();
        return resultados;
    }

    private isi.dan.ms_productos.servicio.ProductoService servicioReal() {
        return new isi.dan.ms_productos.servicio.ProductoService(repository, java.time.Clock.systemUTC());
    }

    @Test
    void busquedaPorNombre_ComodinesDeLike_ShouldMatchLiterally() {
        Producto con = crearProducto("LIT-1", "1.000");
        con.setNombre("Descuento 50% oferta_especial");
        repository.save(con);
        Producto sin = crearProducto("LIT-2", "1.000");
        sin.setNombre("Descuento 500 ofertaXespecial");
        repository.save(sin);
        var servicio = servicioReal();

        assertThat(servicio.searchProductos("50%", null, null, null, null, null, null, null).getContent())
                .extracting(Producto::getCodigo).containsExactly("LIT-1");
        assertThat(servicio.searchProductos("oferta_especial", null, null, null, null, null, null, null).getContent())
                .extracting(Producto::getCodigo).containsExactly("LIT-1");
        assertThat(servicio.searchProductos("%", null, null, null, null, null, null, null).getContent())
                .extracting(Producto::getCodigo).containsExactly("LIT-1");
        assertThat(servicio.searchProductos("\\", null, null, null, null, null, null, null).getContent()).isEmpty();
    }

    @Test
    void listado_ShouldPaginateWithStableOrderAndTotal() {
        for (int i = 0; i < 25; i++) {
            crearProducto(String.format("PAG-%02d", i), "1.000");
        }
        var servicio = servicioReal();

        var primera = servicio.searchProductos("Producto PAG", null, null, null, null, null, 0, 10);
        var ultima = servicio.searchProductos("Producto PAG", null, null, null, null, null, 2, 10);

        assertThat(primera.getTotalElements()).isEqualTo(25);
        assertThat(primera.getContent()).hasSize(10);
        assertThat(ultima.getContent()).hasSize(5);
        assertThat(primera.getContent()).extracting(Producto::getCodigo).first().isEqualTo("PAG-00");
        assertThat(ultima.getContent()).extracting(Producto::getCodigo).last().isEqualTo("PAG-24");
    }
}
