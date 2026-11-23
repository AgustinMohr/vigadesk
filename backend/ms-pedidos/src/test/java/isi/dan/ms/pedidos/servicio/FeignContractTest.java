package isi.dan.ms.pedidos.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.sun.net.httpserver.HttpServer;

import feign.FeignException;
import isi.dan.ms.pedidos.dto.ContextoPedidoDTO;
import isi.dan.ms.pedidos.dto.StockUpdateDTO;
import isi.dan.ms.pedidos.modelo.Producto;

/**
 * Contrato de los clientes Feign hacia ms-clientes y ms-productos. Los clientes reales (los que arma Spring)
 * hablan con un servidor HTTP local que hace de microservicio: se verifican metodo, ruta, parametros,
 * cabeceras y cuerpo que se envian, y como se interpreta la respuesta, incluidos los errores 4xx y 5xx.
 * Si alguien cambia una ruta o un tipo en un cliente Feign, este test falla.
 */
@SpringBootTest
class FeignContractTest {

    /** Lo ultimo que recibio el servidor falso. */
    private record Recibido(String metodo, String rutaYQuery, String contentType, String rol, String cuerpo) {
    }

    private static final AtomicReference<Recibido> RECIBIDO = new AtomicReference<>();
    private static final AtomicReference<Respuesta> RESPUESTA = new AtomicReference<>();

    private record Respuesta(int status, String cuerpo) {
    }

    private static final HttpServer SERVIDOR = iniciarServidor();

    private static HttpServer iniciarServidor() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                byte[] cuerpo = exchange.getRequestBody().readAllBytes();
                RECIBIDO.set(new Recibido(
                        exchange.getRequestMethod(),
                        exchange.getRequestURI().toString(),
                        exchange.getRequestHeaders().getFirst("Content-Type"),
                        exchange.getRequestHeaders().getFirst("X-User-Rol"),
                        new String(cuerpo, StandardCharsets.UTF_8)));
                Respuesta respuesta = RESPUESTA.get();
                byte[] salida = respuesta.cuerpo().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(respuesta.status(), salida.length == 0 ? -1 : salida.length);
                if (salida.length > 0) {
                    exchange.getResponseBody().write(salida);
                }
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void serviciosFalsos(DynamicPropertyRegistry registry) {
        String uri = "http://127.0.0.1:" + SERVIDOR.getAddress().getPort();
        registry.add("spring.cloud.discovery.client.simple.instances.ms-clientes[0].uri", () -> uri);
        registry.add("spring.cloud.discovery.client.simple.instances.ms-productos[0].uri", () -> uri);
    }

    @AfterAll
    static void detener() {
        SERVIDOR.stop(0);
    }

    @Autowired
    private ClienteFeignClient clientes;

    @Autowired
    private ProductoFeignClient productos;

    @BeforeEach
    void limpiar() {
        RECIBIDO.set(null);
        RESPUESTA.set(new Respuesta(200, "{}"));
    }

    private static StockUpdateDTO item(long idProducto, String cantidad) {
        StockUpdateDTO dto = new StockUpdateDTO();
        dto.setIdProducto(idProducto);
        dto.setCantidad(new BigDecimal(cantidad));
        return dto;
    }

    // ---- ms-clientes ----

    @Test
    void getMaximoDescubierto_ShouldCallTheRouteAndDecodeTheAmount() {
        RESPUESTA.set(new Respuesta(200, "250000.50"));

        BigDecimal maximo = clientes.getMaximoDescubierto(5);

        assertThat(RECIBIDO.get().metodo()).isEqualTo("GET");
        assertThat(RECIBIDO.get().rutaYQuery()).isEqualTo("/api/clientes/5/maximo-descubierto");
        assertThat(maximo).isEqualByComparingTo("250000.50");
    }

    @Test
    void checkSaldo_ShouldSendTheAmountAsQueryParameter() {
        RESPUESTA.set(new Respuesta(200, "true"));

        Boolean alcanza = clientes.checkSaldo(5, new BigDecimal("1234.5"));

        assertThat(RECIBIDO.get().metodo()).isEqualTo("GET");
        assertThat(RECIBIDO.get().rutaYQuery()).isEqualTo("/api/clientes/5/saldo?monto=1234.5");
        assertThat(alcanza).isTrue();
    }

    @Test
    void getContextoPedido_ShouldSendObraIdAndDecodeEveryField() {
        RESPUESTA.set(new Respuesta(200, "{\"clienteId\":5,\"clienteNombre\":\"Constructora Sur\","
                + "\"clienteCorreoElectronico\":\"sur@mail.com\",\"clienteCuit\":\"30-1\",\"obraId\":9,"
                + "\"obraDireccion\":\"Calle 1\",\"obraEstado\":\"HABILITADA\",\"obraClienteId\":5,"
                + "\"campoNuevoQueNoConocemos\":true}"));

        ContextoPedidoDTO contexto = clientes.getContextoPedido(5, 9);

        assertThat(RECIBIDO.get().rutaYQuery()).isEqualTo("/api/clientes/5/contexto-pedido?obraId=9");
        assertThat(contexto.getClienteId()).isEqualTo(5);
        assertThat(contexto.getClienteNombre()).isEqualTo("Constructora Sur");
        assertThat(contexto.getClienteCorreoElectronico()).isEqualTo("sur@mail.com");
        assertThat(contexto.getClienteCuit()).isEqualTo("30-1");
        assertThat(contexto.getObraId()).isEqualTo(9);
        assertThat(contexto.getObraDireccion()).isEqualTo("Calle 1");
        assertThat(contexto.getObraEstado()).isEqualTo("HABILITADA");
        assertThat(contexto.getObraClienteId()).isEqualTo(5);
    }

    @Test
    void getContextoPedido_SinObra_ShouldOmitTheQueryParameter() {
        clientes.getContextoPedido(5, null);

        assertThat(RECIBIDO.get().rutaYQuery()).isEqualTo("/api/clientes/5/contexto-pedido");
    }

    @Test
    void clientes_Error404_ShouldRaiseFeignExceptionWithTheStatusAndBody() {
        RESPUESTA.set(new Respuesta(404, "{\"status\":404,\"code\":\"NOT_FOUND\",\"message\":\"Cliente 7 no encontrado\"}"));

        assertThatThrownBy(() -> clientes.getContextoPedido(7, null))
                .isInstanceOfSatisfying(FeignException.class, e -> {
                    assertThat(e.status()).isEqualTo(404);
                    assertThat(e.contentUTF8()).contains("\"code\":\"NOT_FOUND\"");
                });
    }

    // ---- ms-productos ----

    @Test
    void getProducto_ShouldSendTheRoleHeaderAndIgnoreUnknownFields() {
        RESPUESTA.set(new Respuesta(200, "{\"id\":3,\"nombre\":\"Cemento\",\"descripcion\":\"Bolsa 50kg\","
                + "\"unidadMedida\":\"BOLSA\",\"precio\":5000,\"descuentoPromocional\":10,"
                + "\"costo\":3200,\"actualizadoPor\":\"Admin\",\"stockActual\":100}"));

        Producto producto = productos.getProducto(3L, "ADMIN");

        assertThat(RECIBIDO.get().metodo()).isEqualTo("GET");
        assertThat(RECIBIDO.get().rutaYQuery()).isEqualTo("/api/productos/3");
        assertThat(RECIBIDO.get().rol()).isEqualTo("ADMIN");
        assertThat(producto.getId()).isEqualTo(3L);
        assertThat(producto.getNombre()).isEqualTo("Cemento");
        assertThat(producto.getUnidadMedida()).isEqualTo("BOLSA");
        assertThat(producto.getPrecio()).isEqualByComparingTo("5000");
        assertThat(producto.getDescuentoPromocional()).isEqualByComparingTo("10");
    }

    @Test
    void reserveStock_ShouldPutTheItemsAsJsonAndDecodeTheBoolean() {
        RESPUESTA.set(new Respuesta(200, "true"));

        Boolean reservado = productos.reserveStock(List.of(item(1, "2.5"), item(2, "10")));

        Recibido r = RECIBIDO.get();
        assertThat(r.metodo()).isEqualTo("PUT");
        assertThat(r.rutaYQuery()).isEqualTo("/api/productos/stock/reserve");
        assertThat(r.contentType()).startsWith("application/json");
        assertThat(r.cuerpo()).isEqualTo("[{\"idProducto\":1,\"cantidad\":2.5},{\"idProducto\":2,\"cantidad\":10}]");
        assertThat(reservado).isTrue();
    }

    @Test
    void checkStock_ShouldUseItsOwnRoute() {
        RESPUESTA.set(new Respuesta(200, "false"));

        Boolean hay = productos.checkStock(List.of(item(1, "1")));

        assertThat(RECIBIDO.get().metodo()).isEqualTo("PUT");
        assertThat(RECIBIDO.get().rutaYQuery()).isEqualTo("/api/productos/stock/check");
        assertThat(hay).isFalse();
    }

    @Test
    void releaseStock_ShouldPutToReleaseAndAcceptAnEmptyResponse() {
        RESPUESTA.set(new Respuesta(200, ""));

        productos.releaseStock(List.of(item(1, "3")));

        assertThat(RECIBIDO.get().metodo()).isEqualTo("PUT");
        assertThat(RECIBIDO.get().rutaYQuery()).isEqualTo("/api/productos/stock/release");
        assertThat(RECIBIDO.get().cuerpo()).isEqualTo("[{\"idProducto\":1,\"cantidad\":3}]");
    }

    @Test
    void reserveStock_Conflict409_ShouldExposeTheStockErrorCode() {
        RESPUESTA.set(new Respuesta(409, "{\"status\":409,\"code\":\"STOCK_INSUFICIENTE\","
                + "\"message\":\"Stock insuficiente para el producto 1\"}"));

        assertThatThrownBy(() -> productos.reserveStock(List.of(item(1, "999"))))
                .isInstanceOfSatisfying(FeignException.class, e -> {
                    assertThat(e.status()).isEqualTo(409);
                    assertThat(e.contentUTF8()).contains("\"code\":\"STOCK_INSUFICIENTE\"");
                });
    }

    @Test
    void productos_Error503_ShouldRaiseFeignExceptionWithTheStatus() {
        RESPUESTA.set(new Respuesta(503, "{\"status\":503,\"code\":\"SERVICE_UNAVAILABLE\"}"));

        assertThatThrownBy(() -> productos.getProducto(1L, "ADMIN"))
                .isInstanceOfSatisfying(FeignException.class, e -> assertThat(e.status()).isEqualTo(503));
    }
}
