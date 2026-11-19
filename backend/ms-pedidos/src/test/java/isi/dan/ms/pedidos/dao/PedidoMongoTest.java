package isi.dan.ms.pedidos.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import isi.dan.ms.pedidos.modelo.Cliente;
import isi.dan.ms.pedidos.modelo.DetallePedido;
import isi.dan.ms.pedidos.modelo.EstadoPedido;
import isi.dan.ms.pedidos.modelo.Pedido;
import isi.dan.ms.pedidos.modelo.Producto;
import isi.dan.ms.pedidos.servicio.ClienteFeignClient;
import isi.dan.ms.pedidos.servicio.PedidoService;
import isi.dan.ms.pedidos.servicio.ProductoFeignClient;
import isi.dan.ms.pedidos.servicio.SecuenciaService;

/**
 * Pruebas contra MongoDB real: indice unico de idempotencia, consultas del
 * repositorio, busqueda y correlativos concurrentes.
 */
@DataMongoTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "spring.data.mongodb.auto-index-creation=true")
@Import({ PedidoService.class, SecuenciaService.class })
class PedidoMongoTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @MockBean
    private ClienteFeignClient clienteFeignClient;

    @MockBean
    private ProductoFeignClient productoFeignClient;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private SecuenciaService secuenciaService;

    @BeforeEach
    void limpiar() {
        pedidoRepository.deleteAll();
    }

    private Pedido pedido(int clienteId, String clienteNombre, EstadoPedido estado, String key) {
        Cliente cliente = new Cliente();
        cliente.setId(clienteId);
        cliente.setNombre(clienteNombre);

        Producto producto = new Producto();
        producto.setId(1L);
        DetallePedido linea = new DetallePedido();
        linea.setProducto(producto);
        linea.setCantidad(BigDecimal.ONE);

        Pedido p = new Pedido();
        p.setNumeroPedido(secuenciaService.nextNumeroPedido());
        p.setCliente(cliente);
        p.setEstado(estado);
        p.setTotal(BigDecimal.TEN);
        p.setFecha(Instant.now());
        p.setDetalle(List.of(linea));
        p.setIdempotencyKey(key);
        return p;
    }

    @Test
    void idempotencyKey_Duplicate_ShouldBeRejectedByUniqueIndex() {
        pedidoRepository.save(pedido(1, "Acme", EstadoPedido.ACEPTADO, "clave-1"));

        assertThatThrownBy(() -> pedidoRepository.save(pedido(2, "Otra", EstadoPedido.ACEPTADO, "clave-1")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void idempotencyKey_Null_ShouldAllowManyPedidosBecauseIndexIsSparse() {
        pedidoRepository.save(pedido(1, "Acme", EstadoPedido.ACEPTADO, null));
        pedidoRepository.save(pedido(1, "Acme", EstadoPedido.ACEPTADO, null));

        assertThat(pedidoRepository.count()).isEqualTo(2);
    }

    @Test
    void findByIdempotencyKey_ShouldReturnTheSavedPedido() {
        Pedido guardado = pedidoRepository.save(pedido(1, "Acme", EstadoPedido.ACEPTADO, "clave-2"));

        assertThat(pedidoRepository.findByIdempotencyKey("clave-2"))
                .get().extracting(Pedido::getId).isEqualTo(guardado.getId());
        assertThat(pedidoRepository.findByIdempotencyKey("no-existe")).isEmpty();
    }

    @Test
    void findByClienteIdAndEstadoInActivos_ShouldFilterByClienteEstadoAndHabilitado() {
        pedidoRepository.save(pedido(1, "Acme", EstadoPedido.ACEPTADO, null));
        pedidoRepository.save(pedido(1, "Acme", EstadoPedido.EN_PREPARACION, null));
        pedidoRepository.save(pedido(1, "Acme", EstadoPedido.CANCELADO, null));
        pedidoRepository.save(pedido(2, "Otra", EstadoPedido.ACEPTADO, null));
        Pedido borrado = pedido(1, "Acme", EstadoPedido.ACEPTADO, null);
        borrado.setHabilitado(false);
        pedidoRepository.save(borrado);

        List<Pedido> result = pedidoRepository.findByCliente_IdAndEstadoInActivos(
                1, List.of(EstadoPedido.ACEPTADO, EstadoPedido.EN_PREPARACION));

        assertThat(result).hasSize(2)
                .allSatisfy(p -> {
                    assertThat(p.getCliente().getId()).isEqualTo(1);
                    assertThat(p.isHabilitado()).isTrue();
                })
                .extracting(Pedido::getEstado)
                .containsExactlyInAnyOrder(EstadoPedido.ACEPTADO, EstadoPedido.EN_PREPARACION);
    }

    @Test
    void searchPedidos_ShouldCombineFiltersAndIgnoreCaseOnClienteNombre() {
        pedidoRepository.save(pedido(1, "Constructora Norte", EstadoPedido.ACEPTADO, null));
        pedidoRepository.save(pedido(1, "Constructora Norte", EstadoPedido.CANCELADO, null));
        pedidoRepository.save(pedido(2, "Obras Sur", EstadoPedido.ACEPTADO, null));

        assertThat(pedidoService.searchPedidos("norte", null, null)).hasSize(2);
        assertThat(pedidoService.searchPedidos("norte", EstadoPedido.ACEPTADO, null)).hasSize(1);
        assertThat(pedidoService.searchPedidos(null, EstadoPedido.ACEPTADO, 2)).hasSize(1);
        assertThat(pedidoService.searchPedidos(null, null, null)).hasSize(3);
    }

    @Test
    void siguienteNumero_ConcurrentThreads_ShouldNeverRepeat() throws Exception {
        int hilos = 40;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch inicio = new CountDownLatch(1);
        List<Future<Integer>> futuros = new ArrayList<>();
        Callable<Integer> tarea = () -> {
            inicio.await();
            return secuenciaService.nextNumeroRemito();
        };
        for (int i = 0; i < hilos; i++) {
            futuros.add(pool.submit(tarea));
        }
        inicio.countDown();

        Set<Integer> numeros = new HashSet<>();
        for (Future<Integer> f : futuros) {
            numeros.add(f.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(numeros).hasSize(hilos);
    }
}
