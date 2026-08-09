package isi.dan.ms.pedidos.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import isi.dan.ms.pedidos.conf.RabbitMQConfig;
import isi.dan.ms.pedidos.dao.PedidoRepository;
import isi.dan.ms.pedidos.exception.PedidoNotFoundException;
import isi.dan.ms.pedidos.modelo.Cliente;
import isi.dan.ms.pedidos.modelo.DetallePedido;
import isi.dan.ms.pedidos.modelo.EstadoPedido;
import isi.dan.ms.pedidos.modelo.Obra;
import isi.dan.ms.pedidos.modelo.Pedido;
import isi.dan.ms.pedidos.modelo.Producto;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private ClienteFeignClient clienteFeignClient;

    @Mock
    private ProductoFeignClient productoFeignClient;

    @InjectMocks
    private PedidoService pedidoService;

    private Pedido pedido;
    private Cliente cliente;
    private Producto producto;
    private DetallePedido detalle;

    @BeforeEach
    void setUp() {
        cliente = new Cliente();
        cliente.setId(1);
        cliente.setNombre("Test Cliente");

        producto = new Producto();
        producto.setId(1L);
        producto.setNombre("Cemento");
        producto.setPrecio(BigDecimal.valueOf(1000));

        detalle = new DetallePedido();
        detalle.setProducto(producto);
        detalle.setCantidad(5);

        Obra obra = new Obra();
        obra.setId(1);
        obra.setDireccion("Obra Test");

        pedido = new Pedido();
        pedido.setCliente(cliente);
        pedido.setObra(obra);
        pedido.setDetalle(Collections.singletonList(detalle));
        pedido.setUsuario("testuser");
    }

    @Test
    void savePedido_ShouldCalculateTotal() {
        when(clienteFeignClient.getMaximoDescubierto(1)).thenReturn(BigDecimal.valueOf(100000));
        when(clienteFeignClient.checkSaldo(any(), any())).thenReturn(true);
        when(pedidoRepository.findByCliente_IdAndEstadoInActivos(any(), any()))
                .thenReturn(Collections.emptyList());
        when(productoFeignClient.checkStock(any())).thenReturn(true);
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        Pedido result = pedidoService.savePedido(pedido);

        assertThat(result.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(5000));
    }

    @Test
    void savePedido_ShouldSetFechaAndNumeroPedido() {
        when(clienteFeignClient.getMaximoDescubierto(1)).thenReturn(BigDecimal.valueOf(100000));
        when(clienteFeignClient.checkSaldo(any(), any())).thenReturn(true);
        when(pedidoRepository.findByCliente_IdAndEstadoInActivos(any(), any()))
                .thenReturn(Collections.emptyList());
        when(productoFeignClient.checkStock(any())).thenReturn(true);
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        Pedido result = pedidoService.savePedido(pedido);

        assertThat(result.getFecha()).isNotNull();
        assertThat(result.getNumeroPedido()).isPositive();
    }

    @Test
    void savePedido_CreditExceeded_ShouldReturnRECHAZADO() {
        when(clienteFeignClient.getMaximoDescubierto(1)).thenReturn(BigDecimal.valueOf(3000));
        when(pedidoRepository.findByCliente_IdAndEstadoInActivos(any(), any()))
                .thenReturn(Collections.emptyList());
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        Pedido result = pedidoService.savePedido(pedido);

        assertThat(result.getEstado()).isEqualTo(EstadoPedido.RECHAZADO);
    }

    @Test
    void savePedido_ExistingOrdersPlusTotalExceedsCredit_ShouldReturnRECHAZADO() {
        Pedido existingPedido = new Pedido();
        existingPedido.setTotal(BigDecimal.valueOf(96000));
        when(clienteFeignClient.getMaximoDescubierto(1)).thenReturn(BigDecimal.valueOf(100000));
        when(pedidoRepository.findByCliente_IdAndEstadoInActivos(any(), any()))
                .thenReturn(Collections.singletonList(existingPedido));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        Pedido result = pedidoService.savePedido(pedido);

        assertThat(result.getEstado()).isEqualTo(EstadoPedido.RECHAZADO);
    }

    @Test
    void savePedido_ExistingOrdersPlusTotalWithinCredit_ShouldNotReject() {
        Pedido existingPedido = new Pedido();
        existingPedido.setTotal(BigDecimal.valueOf(30000));
        when(clienteFeignClient.getMaximoDescubierto(1)).thenReturn(BigDecimal.valueOf(100000));
        when(clienteFeignClient.checkSaldo(any(), any())).thenReturn(true);
        when(pedidoRepository.findByCliente_IdAndEstadoInActivos(any(), any()))
                .thenReturn(Collections.singletonList(existingPedido));
        when(productoFeignClient.checkStock(any())).thenReturn(true);
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        Pedido result = pedidoService.savePedido(pedido);

        assertThat(result.getEstado()).isNotEqualTo(EstadoPedido.RECHAZADO);
    }

    @Test
    void savePedido_CreditOkAndStockOk_ShouldReturnEN_PREPARACION() {
        when(clienteFeignClient.getMaximoDescubierto(1)).thenReturn(BigDecimal.valueOf(100000));
        when(clienteFeignClient.checkSaldo(any(), any())).thenReturn(true);
        when(pedidoRepository.findByCliente_IdAndEstadoInActivos(any(), any()))
                .thenReturn(Collections.emptyList());
        when(productoFeignClient.checkStock(any())).thenReturn(true);
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        Pedido result = pedidoService.savePedido(pedido);

        assertThat(result.getEstado()).isEqualTo(EstadoPedido.EN_PREPARACION);
    }

    @Test
    void savePedido_CreditOkAndStockOk_ShouldSendRabbitMessages() {
        when(clienteFeignClient.getMaximoDescubierto(1)).thenReturn(BigDecimal.valueOf(100000));
        when(clienteFeignClient.checkSaldo(any(), any())).thenReturn(true);
        when(pedidoRepository.findByCliente_IdAndEstadoInActivos(any(), any()))
                .thenReturn(Collections.emptyList());
        when(productoFeignClient.checkStock(any())).thenReturn(true);
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        pedidoService.savePedido(pedido);

        verify(rabbitTemplate).convertAndSend(RabbitMQConfig.STOCK_UPDATE_QUEUE, "1;5");
    }

    @Test
    void savePedido_CreditOkButStockInsufficient_ShouldReturnACEPTADO() {
        when(clienteFeignClient.getMaximoDescubierto(1)).thenReturn(BigDecimal.valueOf(100000));
        when(clienteFeignClient.checkSaldo(any(), any())).thenReturn(true);
        when(pedidoRepository.findByCliente_IdAndEstadoInActivos(any(), any()))
                .thenReturn(Collections.emptyList());
        when(productoFeignClient.checkStock(any())).thenReturn(false);
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        Pedido result = pedidoService.savePedido(pedido);

        assertThat(result.getEstado()).isEqualTo(EstadoPedido.ACEPTADO);
    }

    @Test
    void savePedido_CreditOkButStockInsufficient_ShouldNotSendRabbitMessages() {
        when(clienteFeignClient.getMaximoDescubierto(1)).thenReturn(BigDecimal.valueOf(100000));
        when(clienteFeignClient.checkSaldo(any(), any())).thenReturn(true);
        when(pedidoRepository.findByCliente_IdAndEstadoInActivos(any(), any()))
                .thenReturn(Collections.emptyList());
        when(productoFeignClient.checkStock(any())).thenReturn(false);
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        pedidoService.savePedido(pedido);

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString());
    }

    @Test
    void savePedido_FeignClientFails_ShouldReturnRECIBIDO() {
        when(clienteFeignClient.getMaximoDescubierto(1)).thenThrow(new RuntimeException("Connection refused"));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        Pedido result = pedidoService.savePedido(pedido);

        assertThat(result.getEstado()).isEqualTo(EstadoPedido.RECIBIDO);
    }

    @Test
    void savePedido_StockCheckFails_ShouldReturnACEPTADO() {
        when(clienteFeignClient.getMaximoDescubierto(1)).thenReturn(BigDecimal.valueOf(100000));
        when(clienteFeignClient.checkSaldo(any(), any())).thenReturn(true);
        when(pedidoRepository.findByCliente_IdAndEstadoInActivos(any(), any()))
                .thenReturn(Collections.emptyList());
        when(productoFeignClient.checkStock(any())).thenThrow(new RuntimeException("Timeout"));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        Pedido result = pedidoService.savePedido(pedido);

        assertThat(result.getEstado()).isEqualTo(EstadoPedido.ACEPTADO);
    }

    @Test
    void savePedido_StockCheckFails_ShouldNotSendRabbitMessages() {
        when(clienteFeignClient.getMaximoDescubierto(1)).thenReturn(BigDecimal.valueOf(100000));
        when(clienteFeignClient.checkSaldo(any(), any())).thenReturn(true);
        when(pedidoRepository.findByCliente_IdAndEstadoInActivos(any(), any()))
                .thenReturn(Collections.emptyList());
        when(productoFeignClient.checkStock(any())).thenThrow(new RuntimeException("Timeout"));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        pedidoService.savePedido(pedido);

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString());
    }

    @Test
    void updateEstado_ENTREGADO_ShouldUpdateAndReturn() {
        when(pedidoRepository.findByIdActivo("1")).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        Pedido result = pedidoService.updateEstado("1", EstadoPedido.ENTREGADO);

        assertThat(result).isNotNull();
        assertThat(result.getEstado()).isEqualTo(EstadoPedido.ENTREGADO);
    }

    @Test
    void updateEstado_CANCELADO_ShouldEnviarDevolucionStock() {
        when(pedidoRepository.findByIdActivo("1")).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        Pedido result = pedidoService.updateEstado("1", EstadoPedido.CANCELADO);

        assertThat(result).isNotNull();
        assertThat(result.getEstado()).isEqualTo(EstadoPedido.CANCELADO);
        verify(rabbitTemplate).convertAndSend(RabbitMQConfig.STOCK_RETURN_QUEUE, "1;5");
    }

    @Test
    void updateEstado_EstadoNoPermitido_ShouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> pedidoService.updateEstado("1", EstadoPedido.RECIBIDO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Solo se permite actualizar a ENTREGADO o CANCELADO");
    }

    @Test
    void updateEstado_PedidoNoExiste_ShouldThrowPedidoNotFoundException() {
        when(pedidoRepository.findByIdActivo("99")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pedidoService.updateEstado("99", EstadoPedido.CANCELADO))
                .isInstanceOf(PedidoNotFoundException.class);
    }

    @Test
    void getAllPedidos_ShouldReturnAll() {
        when(pedidoRepository.findAllActivos()).thenReturn(Collections.singletonList(pedido));

        List<Pedido> result = pedidoService.getAllPedidos();

        assertThat(result).hasSize(1);
    }

    @Test
    void getPedidoById_WhenExists_ShouldReturn() {
        when(pedidoRepository.findByIdActivo("1")).thenReturn(Optional.of(pedido));

        Pedido result = pedidoService.getPedidoById("1");

        assertThat(result).isNotNull();
        assertThat(result.getCliente().getId()).isEqualTo(1);
    }

    @Test
    void getPedidoById_WhenNotExists_ShouldThrowPedidoNotFoundException() {
        when(pedidoRepository.findByIdActivo("99")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pedidoService.getPedidoById("99"))
                .isInstanceOf(PedidoNotFoundException.class);
    }

    @Test
    void deletePedido_ShouldSoftDelete() {
        when(pedidoRepository.findByIdActivo("1")).thenReturn(Optional.of(pedido));

        pedidoService.deletePedido("1");

        assertThat(pedido.isHabilitado()).isFalse();
        assertThat(pedido.getFechaBorrado()).isNotNull();
        verify(pedidoRepository).save(pedido);
    }

    @Test
    void deletePedido_WhenAlreadyDeleted_ShouldDoNothing() {
        when(pedidoRepository.findByIdActivo("1")).thenReturn(Optional.empty());

        pedidoService.deletePedido("1");

        verify(pedidoRepository, never()).save(any(Pedido.class));
    }
}
