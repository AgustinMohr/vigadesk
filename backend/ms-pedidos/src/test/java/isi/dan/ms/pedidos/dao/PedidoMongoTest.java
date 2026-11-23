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
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import isi.dan.ms.pedidos.dto.ContextoPedidoDTO;
import isi.dan.ms.pedidos.modelo.Cliente;
import isi.dan.ms.pedidos.modelo.DetallePedido;
import isi.dan.ms.pedidos.modelo.EstadoPedido;
import isi.dan.ms.pedidos.modelo.Obra;
import isi.dan.ms.pedidos.modelo.Pedido;
import isi.dan.ms.pedidos.modelo.Producto;
import isi.dan.ms.pedidos.servicio.ClienteFeignClient;
import isi.dan.ms.pedidos.servicio.CreditoService;
import isi.dan.ms.pedidos.servicio.PedidoService;
import isi.dan.ms.pedidos.servicio.ProductoFeignClient;
import isi.dan.ms.pedidos.servicio.SecuenciaService;

/**
 * Pruebas contra MongoDB real: indice unico de idempotencia, consultas del
 * repositorio, busqueda y correlativos concurrentes.
 */
@DataMongoTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = { "spring.data.mongodb.auto-index-creation=true",
        "pedidos.limpiar-indices-anteriores=true" })
@Import({ isi.dan.ms.pedidos.conf.ClockConfig.class, PedidoService.class, SecuenciaService.class, CreditoService.class,
        isi.dan.ms.pedidos.conf.LimpiezaIndicesMongo.class })
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

    @Autowired
    private MongoTemplate mongoTemplate;

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
    void idempotencyKey_DuplicateForSameCliente_ShouldBeRejectedByUniqueIndex() {
        pedidoRepository.save(pedido(1, "Acme", EstadoPedido.ACEPTADO, "clave-1"));

        assertThatThrownBy(() -> pedidoRepository.save(pedido(1, "Acme", EstadoPedido.ACEPTADO, "clave-1")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void idempotencyKey_Null_ShouldAllowManyPedidosOfTheSameClienteBecauseIndexIsPartial() {
        pedidoRepository.save(pedido(1, "Acme", EstadoPedido.ACEPTADO, null));
        pedidoRepository.save(pedido(1, "Acme", EstadoPedido.ACEPTADO, null));

        assertThat(pedidoRepository.count()).isEqualTo(2);
    }

    @Test
    void findByClienteIdAndIdempotencyKey_ShouldReturnTheSavedPedidoOnlyForItsCliente() {
        Pedido guardado = pedidoRepository.save(pedido(1, "Acme", EstadoPedido.ACEPTADO, "clave-2"));

        assertThat(pedidoRepository.findByClienteIdAndIdempotencyKey(1, "clave-2"))
                .get().extracting(Pedido::getId).isEqualTo(guardado.getId());
        assertThat(pedidoRepository.findByClienteIdAndIdempotencyKey(1, "no-existe")).isEmpty();
        assertThat(pedidoRepository.findByClienteIdAndIdempotencyKey(2, "clave-2")).isEmpty();
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

        assertThat(pedidoService.buscarPedidos("norte", null, null, null, null).getContent()).hasSize(2);
        assertThat(pedidoService.buscarPedidos("norte", EstadoPedido.ACEPTADO, null, null, null).getContent()).hasSize(1);
        assertThat(pedidoService.buscarPedidos(null, EstadoPedido.ACEPTADO, 2, null, null).getContent()).hasSize(1);
        assertThat(pedidoService.buscarPedidos(null, null, null, null, null).getContent()).hasSize(3);
    }

    @Test
    void buscarPedidos_NombreConMetacaracteresRegex_ShouldMatchLiterally() {
        pedidoRepository.save(pedido(1, "Constructora (Norte) S.A.", EstadoPedido.ACEPTADO, null));
        pedidoRepository.save(pedido(2, "Constructora Norte SXA", EstadoPedido.ACEPTADO, null));

        // sin escapar, "." coincidiria con cualquier caracter y los parentesis serian un grupo
        assertThat(pedidoService.buscarPedidos("(Norte) S.A.", null, null, null, null).getContent())
                .extracting(p -> p.getCliente().getId()).containsExactly(1);
        assertThat(pedidoService.buscarPedidos(".*", null, null, null, null).getContent()).isEmpty();
    }

    @Test
    void buscarPedidos_RegexCatastrofica_ShouldReturnQuicklyWithoutMatches() {
        pedidoRepository.save(pedido(1, "a".repeat(60) + "!", EstadoPedido.ACEPTADO, null));

        long inicio = System.nanoTime();
        var resultado = pedidoService.buscarPedidos("(a+)+$", null, null, null, null);
        long ms = (System.nanoTime() - inicio) / 1_000_000;

        assertThat(resultado.getContent()).isEmpty();
        assertThat(ms).as("tiempo de la busqueda (ms)").isLessThan(2_000);
    }

    @Test
    void buscarPedidos_ShouldPaginateAndReportTheTotal() {
        for (int i = 0; i < 25; i++) {
            pedidoRepository.save(pedido(1, "Constructora Norte", EstadoPedido.ACEPTADO, null));
        }

        var primera = pedidoService.buscarPedidos(null, null, null, 0, 10);
        var tercera = pedidoService.buscarPedidos(null, null, null, 2, 10);
        var fueraDeRango = pedidoService.buscarPedidos(null, null, null, 5, 10);

        assertThat(primera.getContent()).hasSize(10);
        assertThat(primera.getTotalElements()).isEqualTo(25);
        assertThat(tercera.getContent()).hasSize(5);
        assertThat(fueraDeRango.getContent()).isEmpty();
        assertThat(primera.getContent()).extracting(Pedido::getId)
                .doesNotContainAnyElementsOf(tercera.getContent().stream().map(Pedido::getId).toList());
    }

    @Test
    void indices_BusquedaPorClienteYEstadoYOrdenPorFecha_ShouldUseAnIndex() {
        pedidoRepository.save(pedido(1, "Constructora Norte", EstadoPedido.ACEPTADO, null));
        var coleccion = mongoTemplate.getCollection("pedidos");

        var porClienteYEstado = coleccion.find(new org.bson.Document("cliente._id", 1).append("estado", "ACEPTADO"))
                .explain().toJson();
        var porFecha = coleccion.find(new org.bson.Document()).sort(new org.bson.Document("fecha", -1))
                .explain().toJson();

        assertThat(porClienteYEstado).contains("IXSCAN").doesNotContain("COLLSCAN");
        assertThat(porFecha).contains("IXSCAN").contains("fecha_desc").doesNotContain("COLLSCAN");
        assertThat(mongoTemplate.indexOps(Pedido.class).getIndexInfo())
                .extracting(org.springframework.data.mongodb.core.index.IndexInfo::getName)
                .contains("cliente_estado", "fecha_desc");
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

    @Autowired
    private CreditoService creditoService;

    private static final List<EstadoPedido> CONSUMEN = List.of(
            EstadoPedido.ACEPTADO, EstadoPedido.EN_PREPARACION, EstadoPedido.ENTREGA_PARCIAL, EstadoPedido.ENTREGADO);

    @Test
    void consumo_ShouldSumarSoloPedidosActivosDelClienteEnLosEstadosIndicados() {
        pedidoRepository.save(pedido(1, "Acme", EstadoPedido.ACEPTADO, null));
        pedidoRepository.save(pedido(1, "Acme", EstadoPedido.ENTREGADO, null));
        pedidoRepository.save(pedido(1, "Acme", EstadoPedido.CANCELADO, null));
        pedidoRepository.save(pedido(2, "Otra", EstadoPedido.ACEPTADO, null));
        Pedido borrado = pedido(1, "Acme", EstadoPedido.ACEPTADO, null);
        borrado.setHabilitado(false);
        pedidoRepository.save(borrado);

        assertThat(creditoService.consumo(1, CONSUMEN)).isEqualByComparingTo("20");
        assertThat(creditoService.consumo(3, CONSUMEN)).isEqualByComparingTo("0");
    }

    @Test
    void consumo_ShouldLeerTotalesGuardadosComoTextoYComoDecimal128() {
        pedidoRepository.save(pedido(1, "Acme", EstadoPedido.ACEPTADO, null));
        mongoTemplate.getCollection("pedidos").insertOne(new org.bson.Document("cliente", new org.bson.Document("_id", 1))
                .append("estado", "ACEPTADO")
                .append("habilitado", true)
                .append("total", new org.bson.types.Decimal128(new BigDecimal("5.50"))));

        assertThat(creditoService.consumo(1, CONSUMEN)).isEqualByComparingTo("15.50");
    }

    @Test
    void conBloqueo_ConcurrentThreads_ShouldSerializarLaSeccionCritica() throws Exception {
        int hilos = 10;
        int[] contador = { 0 };
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch inicio = new CountDownLatch(1);
        List<Future<Integer>> futuros = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            futuros.add(pool.submit(() -> {
                inicio.await();
                return creditoService.conBloqueo(7, () -> {
                    int leido = contador[0];
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    contador[0] = leido + 1;
                    return leido;
                });
            }));
        }
        inicio.countDown();
        for (Future<Integer> f : futuros) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(contador[0]).isEqualTo(hilos);
    }

    @Test
    void savePedido_ConcurrentOrders_ShouldNeverExceedMaximoDescubierto() throws Exception {
        Producto real = new Producto();
        real.setId(1L);
        real.setNombre("Cemento");
        real.setPrecio(BigDecimal.valueOf(1000));
        org.mockito.Mockito.when(productoFeignClient.getProducto(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(real);
        ContextoPedidoDTO contexto = new ContextoPedidoDTO();
        contexto.setClienteId(1);
        contexto.setClienteNombre("Acme");
        contexto.setObraId(1);
        contexto.setObraClienteId(1);
        contexto.setObraEstado("HABILITADA");
        org.mockito.Mockito.when(clienteFeignClient.getContextoPedido(1, 1)).thenReturn(contexto);
        BigDecimal maximo = BigDecimal.valueOf(3000);
        org.mockito.Mockito.when(clienteFeignClient.getMaximoDescubierto(1)).thenReturn(maximo);
        org.mockito.Mockito.when(clienteFeignClient.checkSaldo(org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.any(BigDecimal.class)))
                .thenAnswer(i -> ((BigDecimal) i.getArgument(1)).compareTo(maximo) <= 0);

        int hilos = 10;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch inicio = new CountDownLatch(1);
        List<Future<Pedido>> futuros = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            final String clave = "concurrente-" + i;
            futuros.add(pool.submit(() -> {
                inicio.await();
                return pedidoService.savePedido(pedidoNuevo(), "ADMIN", clave, "test");
            }));
        }
        inicio.countDown();
        int aceptados = 0;
        for (Future<Pedido> f : futuros) {
            if (f.get(120, TimeUnit.SECONDS).getEstado() != EstadoPedido.RECHAZADO) {
                aceptados++;
            }
        }
        pool.shutdown();

        assertThat(aceptados).isEqualTo(3);
        assertThat(creditoService.consumo(1, CONSUMEN)).isEqualByComparingTo(maximo);
    }

    private Pedido pedidoNuevo() {
        Cliente cliente = new Cliente();
        cliente.setId(1);
        cliente.setNombre("Acme");
        Producto producto = new Producto();
        producto.setId(1L);
        DetallePedido linea = new DetallePedido();
        linea.setProducto(producto);
        linea.setCantidad(BigDecimal.ONE);
        Pedido p = new Pedido();
        p.setCliente(cliente);
        p.setDetalle(new ArrayList<>(List.of(linea)));
        Obra obra = new Obra();
        obra.setId(1);
        p.setObra(obra);
        return p;
    }

    @Autowired
    private isi.dan.ms.pedidos.conf.LimpiezaIndicesMongo limpiezaIndices;

    @Test
    void idempotencyKey_SameValueForDifferentClientes_ShouldBeAllowed() {
        pedidoRepository.save(pedido(1, "Acme", EstadoPedido.ACEPTADO, "compartida"));
        pedidoRepository.save(pedido(2, "Otra", EstadoPedido.ACEPTADO, "compartida"));

        assertThat(pedidoRepository.count()).isEqualTo(2);
    }

    @Test
    void savePedido_MismaClaveYOtroPayload_ShouldThrowConflictWithRealMongo() {
        stubContextoYProducto();
        pedidoService.savePedido(pedidoNuevo(), "ADMIN", "clave-pago", "test");
        Pedido otro = pedidoNuevo();
        otro.getDetalle().get(0).setCantidad(BigDecimal.valueOf(2));

        assertThatThrownBy(() -> pedidoService.savePedido(otro, "ADMIN", "clave-pago", "test"))
                .isInstanceOf(isi.dan.ms.pedidos.exception.ConflictException.class);
        assertThat(pedidoRepository.count()).isEqualTo(1);
    }

    @Test
    void savePedido_MismaClaveYMismoPayload_ShouldDevolverElMismoPedidoWithRealMongo() {
        stubContextoYProducto();
        Pedido primero = pedidoService.savePedido(pedidoNuevo(), "ADMIN", "clave-igual", "test");
        Pedido segundo = pedidoService.savePedido(pedidoNuevo(), "ADMIN", "clave-igual", "test");

        assertThat(segundo.getId()).isEqualTo(primero.getId());
        assertThat(pedidoRepository.count()).isEqualTo(1);
    }

    @Test
    void limpiezaIndices_ShouldEliminarElIndiceGlobalAnteriorYConservarElCompuesto() {
        mongoTemplate.indexOps(Pedido.class).ensureIndex(
                new org.springframework.data.mongodb.core.index.Index()
                        .on("idempotencyKey", org.springframework.data.domain.Sort.Direction.ASC)
                        .named("idempotencyKey").unique().sparse());

        limpiezaIndices.eliminarIndiceGlobalAnterior();

        List<String> nombres = mongoTemplate.indexOps(Pedido.class).getIndexInfo().stream()
                .map(org.springframework.data.mongodb.core.index.IndexInfo::getName).toList();
        assertThat(nombres).doesNotContain("idempotencyKey").contains("cliente_idempotencyKey");
        // Con el indice global fuera, otro cliente puede usar la misma clave
        pedidoRepository.save(pedido(1, "Acme", EstadoPedido.ACEPTADO, "k"));
        pedidoRepository.save(pedido(2, "Otra", EstadoPedido.ACEPTADO, "k"));
        assertThat(pedidoRepository.count()).isEqualTo(2);
    }

    private void stubContextoYProducto() {
        Producto real = new Producto();
        real.setId(1L);
        real.setNombre("Cemento");
        real.setPrecio(BigDecimal.valueOf(1000));
        org.mockito.Mockito.when(productoFeignClient.getProducto(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(real);
        org.mockito.Mockito.when(clienteFeignClient.getMaximoDescubierto(1)).thenReturn(BigDecimal.valueOf(100000));
        org.mockito.Mockito.when(clienteFeignClient.checkSaldo(org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.any(BigDecimal.class))).thenReturn(true);
        ContextoPedidoDTO contexto = new ContextoPedidoDTO();
        contexto.setClienteId(1);
        contexto.setClienteNombre("Acme");
        contexto.setObraId(1);
        contexto.setObraClienteId(1);
        contexto.setObraEstado("HABILITADA");
        org.mockito.Mockito.when(clienteFeignClient.getContextoPedido(1, 1)).thenReturn(contexto);
    }
}
