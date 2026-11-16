package isi.dan.ms.pedidos.servicio;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import isi.dan.ms.pedidos.modelo.Cliente;
import isi.dan.ms.pedidos.modelo.DetallePedido;
import isi.dan.ms.pedidos.modelo.Entrega;
import isi.dan.ms.pedidos.modelo.EntregaItem;
import isi.dan.ms.pedidos.modelo.EstadoPedido;
import isi.dan.ms.pedidos.modelo.Obra;
import isi.dan.ms.pedidos.modelo.Pedido;
import isi.dan.ms.pedidos.modelo.Producto;

class PedidoPdfServiceTest {

    private PedidoPdfService service;
    private Pedido pedido;
    private Entrega entrega;

    @BeforeEach
    void setUp() {
        service = new PedidoPdfService();
        ReflectionTestUtils.setField(service, "corralonNombre", "Corralon Test");
        ReflectionTestUtils.setField(service, "corralonCuit", "30-00000000-0");
        ReflectionTestUtils.setField(service, "corralonDireccion", "Calle 123");
        ReflectionTestUtils.setField(service, "corralonTelefono", "111-222");

        Producto producto = new Producto();
        producto.setId(1L);
        producto.setNombre("Arena");
        producto.setUnidadMedida("M3");

        DetallePedido detalle = new DetallePedido();
        detalle.setLineaId("linea-1");
        detalle.setProducto(producto);
        detalle.setCantidad(new BigDecimal("1.5"));
        detalle.setPrecioUnitario(BigDecimal.valueOf(10000));
        detalle.setDescuento(BigDecimal.ZERO);
        detalle.setPrecioFinal(BigDecimal.valueOf(15000));

        Cliente cliente = new Cliente();
        cliente.setId(1);
        cliente.setNombre("Constructora Test");
        Obra obra = new Obra();
        obra.setId(1);
        obra.setDireccion("Obra Test");

        pedido = new Pedido();
        pedido.setId("p1");
        pedido.setNumeroPedido(1001);
        pedido.setFecha(Instant.now());
        pedido.setCliente(cliente);
        pedido.setObra(obra);
        pedido.setEstado(EstadoPedido.EN_PREPARACION);
        pedido.setTotal(BigDecimal.valueOf(15000));
        pedido.setDetalle(new ArrayList<>(List.of(detalle)));

        EntregaItem item = new EntregaItem();
        item.setLineaId("linea-1");
        item.setIdProducto(1L);
        item.setProductoNombre("Arena");
        item.setCantidad(new BigDecimal("1.5"));

        entrega = new Entrega();
        entrega.setId("e1");
        entrega.setNumeroRemito(2001);
        entrega.setFecha(Instant.now());
        entrega.setReceptor("Juan Obra");
        entrega.setItems(new ArrayList<>(List.of(item)));
    }

    @Test
    void generarPedido_ShouldReturnPdf() {
        byte[] pdf = service.generarPedido(pedido);

        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        assertThat(pdf.length).isGreaterThan(500);
    }

    @Test
    void generarRemito_ShouldReturnPdf() {
        byte[] pdf = service.generarRemito(pedido, entrega);

        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        assertThat(pdf.length).isGreaterThan(500);
    }
}
