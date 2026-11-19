package isi.dan.ms.pedidos.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import feign.FeignException;
import feign.Request;
import isi.dan.ms.pedidos.modelo.AuditLog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import isi.dan.ms.pedidos.dao.PedidoRepository;
import isi.dan.ms.pedidos.dto.ContextoPedidoDTO;
import isi.dan.ms.pedidos.dto.EntregaItemRequest;
import isi.dan.ms.pedidos.dto.EntregaRequest;
import isi.dan.ms.pedidos.exception.ConflictException;
import isi.dan.ms.pedidos.exception.ExternalServiceUnavailableException;
import isi.dan.ms.pedidos.exception.PedidoNotFoundException;
import isi.dan.ms.pedidos.exception.UnprocessableEntityException;
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
    private MongoTemplate mongoTemplate;

    @Mock
    private SecuenciaService secuenciaService;

    @Mock
    private ClienteFeignClient clienteFeignClient;

    @Mock
    private ProductoFeignClient productoFeignClient;

    @Mock
    private CreditoService creditoService;

    @InjectMocks
    private PedidoService pedidoService;

    private Pedido pedido;
    private Cliente cliente;
    private Producto productoReal;

    @BeforeEach
    void setUp() {
        cliente = new Cliente();
        cliente.setId(1);
        cliente.setNombre("Test Cliente");

        productoReal = new Producto();
        productoReal.setId(1L);
        productoReal.setNombre("Cemento");
        productoReal.setPrecio(BigDecimal.valueOf(1000));

        Producto productoDelPayload = new Producto();
        productoDelPayload.setId(1L);
        productoDelPayload.setNombre("Cemento trucado");
        productoDelPayload.setPrecio(BigDecimal.ONE);
        productoDelPayload.setDescuentoPromocional(BigDecimal.valueOf(100));

        DetallePedido detalle = new DetallePedido();
        detalle.setLineaId("linea-1");
        detalle.setProducto(productoDelPayload);
        detalle.setCantidad(BigDecimal.valueOf(5));

        Obra obra = new Obra();
        obra.setId(1);
        obra.setDireccion("Obra Test");

        pedido = new Pedido();
        pedido.setCliente(cliente);
        pedido.setObra(obra);
        pedido.setDetalle(Collections.singletonList(detalle));
        pedido.setUsuario("payload-hacker");

        ContextoPedidoDTO contexto = new ContextoPedidoDTO();
        contexto.setClienteId(1);
        contexto.setClienteNombre("Nombre real");
        contexto.setClienteCuit("30-11111111-1");
        contexto.setClienteCorreoElectronico("real@cliente.com");
        contexto.setObraId(1);
        contexto.setObraDireccion("Obra real");
        contexto.setObraEstado("HABILITADA");
        contexto.setObraClienteId(1);
        lenient().when(clienteFeignClient.getContextoPedido(1, 1)).thenReturn(contexto);
        lenient().when(creditoService.conBloqueo(any(), any())).thenAnswer(i -> {
            java.util.function.Supplier<?> accion = i.getArgument(1);
            return accion.get();
        });
        lenient().when(secuenciaService.nextNumeroPedido()).thenReturn(1000);
        lenient().when(secuenciaService.nextNumeroRemito()).thenReturn(1);
        lenient().when(pedidoRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        lenient().when(productoFeignClient.getProducto(anyLong(), anyString())).thenReturn(productoReal);
        lenient().when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));
    }

    private void stubCreditoOk() {
        when(clienteFeignClient.getMaximoDescubierto(1)).thenReturn(BigDecimal.valueOf(100000));
        when(clienteFeignClient.checkSaldo(any(), any())).thenReturn(true);
        when(creditoService.consumo(any(), any()))
                .thenReturn(BigDecimal.ZERO);
    }

    private void stubStockOk() {
        when(productoFeignClient.reserveStock(any())).thenReturn(true);
    }

    private EntregaRequest entregaRequest(String cantidad) {
        EntregaItemRequest item = new EntregaItemRequest();
        item.setLineaId("linea-1");
        item.setCantidad(new BigDecimal(cantidad));

        EntregaRequest request = new EntregaRequest();
        request.setReceptor("Juan Obra");
        request.setItems(new java.util.ArrayList<>(List.of(item)));
        return request;
    }

    @Test
    void savePedido_ShouldIgnorePayloadPriceAndUseCatalogPrice() {
        stubCreditoOk();
        stubStockOk();

        Pedido result = pedidoService.savePedido(pedido);

        assertThat(result.getDetalle().get(0).getPrecioUnitario())
                .isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(result.getDetalle().get(0).getDescuento())
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(5000));
        assertThat(result.getDetalle().get(0).getProducto().getNombre()).isEqualTo("Cemento");
    }

    @Test
    void savePedido_ShouldCalculateLineAmountsWithCatalogDiscount() {
        productoReal.setDescuentoPromocional(BigDecimal.valueOf(10));
        stubCreditoOk();
        stubStockOk();

        Pedido result = pedidoService.savePedido(pedido);

        DetallePedido linea = result.getDetalle().get(0);
        assertThat(linea.getPrecioUnitario()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(linea.getDescuento()).isEqualByComparingTo(BigDecimal.valueOf(10));
        assertThat(linea.getPrecioFinal()).isEqualByComparingTo(BigDecimal.valueOf(4500));
        assertThat(result.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(4500));
    }

    @Test
    void savePedido_DiscountOutOfRange_ShouldClampAndNeverProduceNegativeTotal() {
        productoReal.setDescuentoPromocional(BigDecimal.valueOf(10000));
        stubCreditoOk();
        stubStockOk();

        Pedido result = pedidoService.savePedido(pedido);

        DetallePedido linea = result.getDetalle().get(0);
        assertThat(linea.getDescuento()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(linea.getPrecioFinal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void savePedido_DiscountNull_ShouldTreatAsNoDiscount() {
        productoReal.setDescuentoPromocional(null);
        stubCreditoOk();
        stubStockOk();

        Pedido result = pedidoService.savePedido(pedido);

        assertThat(result.getDetalle().get(0).getDescuento()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(5000));
    }

    @Test
    void savePedido_ShouldSetFechaAndSequentialNumeroPedido() {
        stubCreditoOk();
        stubStockOk();

        Pedido result = pedidoService.savePedido(pedido);

        assertThat(result.getFecha()).isNotNull();
        assertThat(result.getNumeroPedido()).isEqualTo(1000);
        assertThat(result.getAuditoria()).isNotEmpty();
        verify(secuenciaService).nextNumeroPedido();
    }

    @Test
    void savePedido_ShouldOverrideUsuarioFromAuthenticatedActor() {
        stubCreditoOk();
        stubStockOk();

        Pedido result = pedidoService.savePedido(pedido, "CLIENTE", "key-1", "Juan Perez");

        assertThat(result.getUsuario()).isEqualTo("Juan Perez");
    }

    @Test
    void savePedido_WithExistingIdempotencyKey_ShouldReturnExistingWithoutRecalculate() {
        Pedido existente = new Pedido();
        existente.setId("pedido-existente");
        existente.setTotal(BigDecimal.valueOf(123));
        when(pedidoRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existente));

        Pedido result = pedidoService.savePedido(pedido, "CLIENTE", "key-1", "Juan Perez");

        assertThat(result.getId()).isEqualTo("pedido-existente");
        verify(productoFeignClient, never()).getProducto(anyLong(), anyString());
    }

    @Test
    void savePedido_ConcurrentDuplicateKey_ShouldReleaseDuplicateReservation() {
        Pedido existente = new Pedido();
        existente.setId("pedido-existente");
        when(pedidoRepository.findByIdempotencyKey("key-1"))
                .thenReturn(Optional.empty(), Optional.of(existente));
        stubCreditoOk();
        stubStockOk();
        when(pedidoRepository.save(any(Pedido.class)))
                .thenThrow(new DuplicateKeyException("idempotencyKey"));

        Pedido result = pedidoService.savePedido(pedido, "CLIENTE", "key-1", "Juan Perez");

        assertThat(result.getId()).isEqualTo("pedido-existente");
        verify(productoFeignClient).releaseStock(any());
    }

    @Test
    void savePedido_CreditExceeded_ShouldReturnRECHAZADO() {
        when(clienteFeignClient.getMaximoDescubierto(1)).thenReturn(BigDecimal.valueOf(3000));
        when(creditoService.consumo(any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(clienteFeignClient.checkSaldo(any(), any())).thenReturn(false);

        Pedido result = pedidoService.savePedido(pedido);

        assertThat(result.getEstado()).isEqualTo(EstadoPedido.RECHAZADO);
        verify(productoFeignClient, never()).reserveStock(any());
    }

    @Test
    void savePedido_ExistingDeliveredOrderConsumesCredit_ShouldReturnRECHAZADO() {
        Pedido entregado = new Pedido();
        entregado.setTotal(BigDecimal.valueOf(96000));
        when(clienteFeignClient.getMaximoDescubierto(1)).thenReturn(BigDecimal.valueOf(100000));
        when(creditoService.consumo(eq(1), any()))
                .thenReturn(entregado.getTotal());
        when(clienteFeignClient.checkSaldo(1, BigDecimal.valueOf(101000))).thenReturn(false);

        Pedido result = pedidoService.savePedido(pedido);

        assertThat(result.getEstado()).isEqualTo(EstadoPedido.RECHAZADO);
    }

    @Test
    void savePedido_CreditOkAndStockOk_ShouldReturnEN_PREPARACION() {
        stubCreditoOk();
        stubStockOk();

        Pedido result = pedidoService.savePedido(pedido);

        assertThat(result.getEstado()).isEqualTo(EstadoPedido.EN_PREPARACION);
    }

    @Test
    void savePedido_CreditOkAndStockOk_ShouldReserveStock() {
        stubCreditoOk();
        stubStockOk();

        pedidoService.savePedido(pedido);

        verify(productoFeignClient).reserveStock(any());
    }

    @Test
    void savePedido_CreditOkButStockInsufficient_ShouldReturnACEPTADO() {
        stubCreditoOk();
        when(productoFeignClient.reserveStock(any())).thenThrow(new FeignException.Conflict(
                "STOCK_INSUFICIENTE",
                Request.create(Request.HttpMethod.PUT, "/api/productos/stock/reserve", Map.of(), null,
                        StandardCharsets.UTF_8, null),
                null, null));

        Pedido result = pedidoService.savePedido(pedido);

        assertThat(result.getEstado()).isEqualTo(EstadoPedido.ACEPTADO);
        assertThat(result.getAuditoria()).extracting(AuditLog::getAccion)
                .contains("PEDIDO_PENDIENTE_STOCK");
    }

    @Test
    void savePedido_CreditOkAndStockServiceDown_ShouldFailAndNotSave() {
        stubCreditoOk();
        when(productoFeignClient.reserveStock(any())).thenThrow(new RuntimeException("Timeout"));

        assertThatThrownBy(() -> pedidoService.savePedido(pedido))
                .isInstanceOf(ExternalServiceUnavailableException.class);

        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    void savePedido_CreditServiceDown_ShouldFailClosedAndNotSave() {
        when(clienteFeignClient.getMaximoDescubierto(1)).thenThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> pedidoService.savePedido(pedido))
                .isInstanceOf(ExternalServiceUnavailableException.class);

        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    void savePedido_ProductServiceDown_ShouldFailClosedAndNotSave() {
        when(productoFeignClient.getProducto(anyLong(), anyString()))
                .thenThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> pedidoService.savePedido(pedido))
                .isInstanceOf(ExternalServiceUnavailableException.class);

        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    void savePedido_WhenPersistenceFailsAfterReserve_ShouldCompensateStock() {
        stubCreditoOk();
        stubStockOk();
        when(pedidoRepository.save(any(Pedido.class))).thenThrow(new RuntimeException("DB down"));

        assertThatThrownBy(() -> pedidoService.savePedido(pedido))
                .isInstanceOf(RuntimeException.class);

        verify(productoFeignClient).releaseStock(any());
    }

    @Test
    void updateEstado_ENTREGADO_FromEN_PREPARACIONWithPending_ShouldThrowConflict() {
        pedido.setEstado(EstadoPedido.EN_PREPARACION);
        when(pedidoRepository.findByIdActivo("1")).thenReturn(Optional.of(pedido));

        assertThatThrownBy(() -> pedidoService.updateEstado("1", EstadoPedido.ENTREGADO))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("cantidades pendientes");
    }

    @Test
    void updateEstado_ENTREGADO_WhenAllLinesDelivered_ShouldUpdate() {
        pedido.setEstado(EstadoPedido.EN_PREPARACION);
        pedido.getDetalle().get(0).setCantidadEntregada(BigDecimal.valueOf(5));
        when(pedidoRepository.findByIdActivo("1")).thenReturn(Optional.of(pedido));

        Pedido result = pedidoService.updateEstado("1", EstadoPedido.ENTREGADO);

        assertThat(result.getEstado()).isEqualTo(EstadoPedido.ENTREGADO);
    }

    @Test
    void registrarEntrega_Partial_ShouldUpdateDeliveredQuantityAndState() {
        pedido.setEstado(EstadoPedido.EN_PREPARACION);
        when(pedidoRepository.findByIdActivo("1")).thenReturn(Optional.of(pedido));

        Pedido result = pedidoService.registrarEntrega("1", entregaRequest("2"), "Deposito");

        assertThat(result.getEstado()).isEqualTo(EstadoPedido.ENTREGA_PARCIAL);
        assertThat(result.getDetalle().get(0).getCantidadEntregada())
                .isEqualByComparingTo(BigDecimal.valueOf(2));
        assertThat(result.getDetalle().get(0).getCantidadPendiente())
                .isEqualByComparingTo(BigDecimal.valueOf(3));
        assertThat(result.getEntregas()).hasSize(1);
        assertThat(result.getEntregas().get(0).getNumeroRemito()).isEqualTo(1);
        assertThat(result.getAuditoria()).isNotEmpty();
    }

    @Test
    void registrarEntrega_Full_ShouldTransitionToENTREGADO() {
        pedido.setEstado(EstadoPedido.EN_PREPARACION);
        when(pedidoRepository.findByIdActivo("1")).thenReturn(Optional.of(pedido));

        Pedido result = pedidoService.registrarEntrega("1", entregaRequest("5"), "Deposito");

        assertThat(result.getEstado()).isEqualTo(EstadoPedido.ENTREGADO);
        assertThat(result.getDetalle().get(0).getCantidadPendiente()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void registrarEntrega_OverDelivery_ShouldThrowUnprocessable() {
        pedido.setEstado(EstadoPedido.EN_PREPARACION);
        when(pedidoRepository.findByIdActivo("1")).thenReturn(Optional.of(pedido));

        assertThatThrownBy(() -> pedidoService.registrarEntrega("1", entregaRequest("6"), "Deposito"))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("supera la pendiente");
    }

    @Test
    void reintentarReserva_WithStock_ShouldMoveToEnPreparacion() {
        pedido.setEstado(EstadoPedido.ACEPTADO);
        when(pedidoRepository.findByIdActivo("1")).thenReturn(Optional.of(pedido));
        when(productoFeignClient.reserveStock(any())).thenReturn(true);
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        Pedido result = pedidoService.reintentarReserva("1", "Admin");

        assertThat(result.getEstado()).isEqualTo(EstadoPedido.EN_PREPARACION);
        assertThat(result.getAuditoria()).extracting(AuditLog::getAccion).contains("RESERVA_CONFIRMADA");
    }

    @Test
    void reintentarReserva_WithoutStock_ShouldThrowConflictAndKeepACEPTADO() {
        pedido.setEstado(EstadoPedido.ACEPTADO);
        when(pedidoRepository.findByIdActivo("1")).thenReturn(Optional.of(pedido));
        when(productoFeignClient.reserveStock(any())).thenThrow(new FeignException.Conflict(
                "STOCK_INSUFICIENTE",
                Request.create(Request.HttpMethod.PUT, "/api/productos/stock/reserve", Map.of(), null,
                        StandardCharsets.UTF_8, null),
                null, null));

        assertThatThrownBy(() -> pedidoService.reintentarReserva("1", "Admin"))
                .isInstanceOf(ConflictException.class);

        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.ACEPTADO);
    }

    @Test
    void reintentarReserva_StockServiceDown_ShouldThrowUnavailableAndNotSave() {
        pedido.setEstado(EstadoPedido.ACEPTADO);
        when(pedidoRepository.findByIdActivo("1")).thenReturn(Optional.of(pedido));
        when(productoFeignClient.reserveStock(any())).thenThrow(new RuntimeException("Timeout"));

        assertThatThrownBy(() -> pedidoService.reintentarReserva("1", "Admin"))
                .isInstanceOf(ExternalServiceUnavailableException.class);

        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    void reintentarReserva_FromOtherState_ShouldThrowConflictWithoutCallingStock() {
        pedido.setEstado(EstadoPedido.EN_PREPARACION);
        when(pedidoRepository.findByIdActivo("1")).thenReturn(Optional.of(pedido));

        assertThatThrownBy(() -> pedidoService.reintentarReserva("1", "Admin"))
                .isInstanceOf(ConflictException.class);

        verify(productoFeignClient, never()).reserveStock(any());
    }

    @Test
    void registrarEntrega_FromACEPTADO_ShouldThrowConflict() {
        pedido.setEstado(EstadoPedido.ACEPTADO);
        when(pedidoRepository.findByIdActivo("1")).thenReturn(Optional.of(pedido));

        assertThatThrownBy(() -> pedidoService.registrarEntrega("1", entregaRequest("1"), "Deposito"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void registrarEntrega_DuplicateLine_ShouldThrowUnprocessable() {
        pedido.setEstado(EstadoPedido.EN_PREPARACION);
        when(pedidoRepository.findByIdActivo("1")).thenReturn(Optional.of(pedido));
        EntregaRequest request = entregaRequest("1");
        request.getItems().add(request.getItems().get(0));

        assertThatThrownBy(() -> pedidoService.registrarEntrega("1", request, "Deposito"))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("repetida");
    }

    @Test
    void updateEstado_ENTREGADO_FromACEPTADO_ShouldThrowConflict() {
        pedido.setEstado(EstadoPedido.ACEPTADO);
        when(pedidoRepository.findByIdActivo("1")).thenReturn(Optional.of(pedido));

        assertThatThrownBy(() -> pedidoService.updateEstado("1", EstadoPedido.ENTREGADO))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateEstado_CANCELADO_FromEN_PREPARACION_ShouldReleaseStock() {
        pedido.setEstado(EstadoPedido.EN_PREPARACION);
        when(pedidoRepository.findByIdActivo("1")).thenReturn(Optional.of(pedido));

        Pedido result = pedidoService.updateEstado("1", EstadoPedido.CANCELADO);

        assertThat(result.getEstado()).isEqualTo(EstadoPedido.CANCELADO);
        verify(productoFeignClient).releaseStock(any());
    }

    @Test
    void updateEstado_CANCELADO_FromACEPTADO_ShouldNotReleaseStock() {
        pedido.setEstado(EstadoPedido.ACEPTADO);
        when(pedidoRepository.findByIdActivo("1")).thenReturn(Optional.of(pedido));

        Pedido result = pedidoService.updateEstado("1", EstadoPedido.CANCELADO);

        assertThat(result.getEstado()).isEqualTo(EstadoPedido.CANCELADO);
        verify(productoFeignClient, never()).releaseStock(any());
    }

    @Test
    void updateEstado_CANCELADO_WithDeliveries_ShouldThrowConflict() {
        pedido.setEstado(EstadoPedido.ENTREGA_PARCIAL);
        when(pedidoRepository.findByIdActivo("1")).thenReturn(Optional.of(pedido));

        assertThatThrownBy(() -> pedidoService.updateEstado("1", EstadoPedido.CANCELADO))
                .isInstanceOf(ConflictException.class);
        verify(productoFeignClient, never()).releaseStock(any());
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
    void getEntregas_WhenOrderHasDeliveries_ShouldReturnThem() {
        pedido.setEstado(EstadoPedido.EN_PREPARACION);
        when(pedidoRepository.findByIdActivo("1")).thenReturn(Optional.of(pedido));
        pedidoService.registrarEntrega("1", entregaRequest("2"), "Deposito");

        List<isi.dan.ms.pedidos.modelo.Entrega> result = pedidoService.getEntregas("1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getReceptor()).isEqualTo("Juan Obra");
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
    void deletePedido_FromEN_PREPARACION_ShouldReleaseStock() {
        pedido.setEstado(EstadoPedido.EN_PREPARACION);
        when(pedidoRepository.findByIdActivo("1")).thenReturn(Optional.of(pedido));

        pedidoService.deletePedido("1");

        verify(productoFeignClient).releaseStock(any());
        assertThat(pedido.isHabilitado()).isFalse();
    }

    @Test
    void deletePedido_WithDeliveries_ShouldThrowConflict() {
        pedido.setEstado(EstadoPedido.ENTREGADO);
        when(pedidoRepository.findByIdActivo("1")).thenReturn(Optional.of(pedido));

        assertThatThrownBy(() -> pedidoService.deletePedido("1"))
                .isInstanceOf(ConflictException.class);
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    void deletePedido_WhenAlreadyDeleted_ShouldDoNothing() {
        when(pedidoRepository.findByIdActivo("1")).thenReturn(Optional.empty());

        pedidoService.deletePedido("1");

        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    void searchPedidos_ByClienteId_ShouldReturnPedidos() {
        when(mongoTemplate.find(any(Query.class), eq(Pedido.class))).thenReturn(Collections.singletonList(pedido));

        List<Pedido> result = pedidoService.searchPedidos(null, null, 1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCliente().getId()).isEqualTo(1);
    }

    @Test
    void searchPedidos_ByEstado_ShouldReturnPedidos() {
        when(mongoTemplate.find(any(Query.class), eq(Pedido.class))).thenReturn(Collections.singletonList(pedido));

        List<Pedido> result = pedidoService.searchPedidos(null, EstadoPedido.ENTREGADO, null);

        assertThat(result).hasSize(1);
    }

    @Test
    void searchPedidos_ByClienteNombre_ShouldReturnPedidos() {
        when(mongoTemplate.find(any(Query.class), eq(Pedido.class))).thenReturn(Collections.singletonList(pedido));

        List<Pedido> result = pedidoService.searchPedidos("Constructora", null, null);

        assertThat(result).hasSize(1);
    }

    @Test
    void searchPedidos_SinFiltros_ShouldReturnPedidos() {
        when(mongoTemplate.find(any(Query.class), eq(Pedido.class))).thenReturn(Collections.singletonList(pedido));

        List<Pedido> result = pedidoService.searchPedidos(null, null, null);

        assertThat(result).hasSize(1);
    }

    private ContextoPedidoDTO contextoObra(Integer obraClienteId, String estado) {
        ContextoPedidoDTO c = new ContextoPedidoDTO();
        c.setClienteId(1);
        c.setClienteNombre("Nombre real");
        c.setObraId(1);
        c.setObraClienteId(obraClienteId);
        c.setObraEstado(estado);
        return c;
    }

    @Test
    void savePedido_ShouldTakeClienteNombreCuitAndObraFromMsClientes() {
        stubCreditoOk();
        stubStockOk();
        pedido.getCliente().setNombre("Nombre trucado");
        pedido.getCliente().setCuit("00-00000000-0");
        pedido.getObra().setDireccion("Direccion trucada");

        Pedido result = pedidoService.savePedido(pedido);

        assertThat(result.getCliente().getNombre()).isEqualTo("Nombre real");
        assertThat(result.getCliente().getCuit()).isEqualTo("30-11111111-1");
        assertThat(result.getObra().getDireccion()).isEqualTo("Obra real");
    }

    @Test
    void savePedido_ObraDeOtroCliente_ShouldThrowUnprocessableAndNotSave() {
        when(clienteFeignClient.getContextoPedido(1, 1)).thenReturn(contextoObra(2, "HABILITADA"));

        assertThatThrownBy(() -> pedidoService.savePedido(pedido))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("no pertenece al cliente");

        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    void savePedido_ObraNoHabilitada_ShouldThrowUnprocessable() {
        when(clienteFeignClient.getContextoPedido(1, 1)).thenReturn(contextoObra(1, "FINALIZADA"));

        assertThatThrownBy(() -> pedidoService.savePedido(pedido))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("no esta habilitada");
    }

    @Test
    void savePedido_ObraInexistente_ShouldThrowUnprocessable() {
        ContextoPedidoDTO sinObra = contextoObra(null, null);
        sinObra.setObraId(null);
        when(clienteFeignClient.getContextoPedido(1, 1)).thenReturn(sinObra);

        assertThatThrownBy(() -> pedidoService.savePedido(pedido))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("no existe");
    }

    @Test
    void savePedido_SinObra_ShouldThrowUnprocessable() {
        pedido.setObra(null);

        assertThatThrownBy(() -> pedidoService.savePedido(pedido))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("obra es obligatoria");
    }

    @Test
    void savePedido_ClienteInexistente_ShouldThrowUnprocessable() {
        when(clienteFeignClient.getContextoPedido(1, 1)).thenThrow(new FeignException.NotFound(
                "no existe",
                Request.create(Request.HttpMethod.GET, "/api/clientes/1/contexto-pedido", Map.of(), null,
                        StandardCharsets.UTF_8, null),
                null, null));

        assertThatThrownBy(() -> pedidoService.savePedido(pedido))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("cliente 1 no existe");
    }

    @Test
    void savePedido_MsClientesCaido_ShouldFailClosedAndNotSave() {
        when(clienteFeignClient.getContextoPedido(1, 1)).thenThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> pedidoService.savePedido(pedido))
                .isInstanceOf(ExternalServiceUnavailableException.class);

        verify(pedidoRepository, never()).save(any(Pedido.class));
    }
}
