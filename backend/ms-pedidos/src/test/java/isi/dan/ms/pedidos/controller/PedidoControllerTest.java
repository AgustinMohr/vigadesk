package isi.dan.ms.pedidos.controller;

import com.fasterxml.jackson.databind.ObjectMapper;

import isi.dan.ms.pedidos.dto.CrearPedidoRequest;
import isi.dan.ms.pedidos.dto.EstadoUpdateRequest;
import isi.dan.ms.pedidos.exception.PedidoNotFoundException;
import isi.dan.ms.pedidos.modelo.Cliente;
import isi.dan.ms.pedidos.modelo.DetallePedido;
import isi.dan.ms.pedidos.modelo.EstadoPedido;
import isi.dan.ms.pedidos.modelo.Obra;
import isi.dan.ms.pedidos.modelo.Pedido;
import isi.dan.ms.pedidos.modelo.Producto;
import isi.dan.ms.pedidos.servicio.PedidoPdfService;
import isi.dan.ms.pedidos.servicio.PedidoService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PedidoController.class)
class PedidoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PedidoService pedidoService;

    @MockBean
    private PedidoPdfService pedidoPdfService;

    private Pedido pedido;

    @BeforeEach
    void setUp() {
        Cliente cliente = new Cliente();
        cliente.setId(1);
        cliente.setNombre("Test Cliente");

        Obra obra = new Obra();
        obra.setId(1);
        obra.setDireccion("Test Obra");

        Producto prod = new Producto();
        prod.setId(1L);
        prod.setNombre("Producto Test");
        prod.setPrecio(BigDecimal.valueOf(1000));

        DetallePedido detalle = new DetallePedido();
        detalle.setProducto(prod);
        detalle.setCantidad(BigDecimal.valueOf(2));
        detalle.setPrecioUnitario(BigDecimal.valueOf(1000));

        pedido = new Pedido();
        pedido.setId("pedido-1");
        pedido.setNumeroPedido(1001);
        pedido.setCliente(cliente);
        pedido.setObra(obra);
        pedido.setTotal(BigDecimal.valueOf(2000));
        pedido.setEstado(EstadoPedido.RECIBIDO);
        pedido.setDetalle(Collections.singletonList(detalle));
    }

    @Test
    void testGetAll() throws Exception {
        Mockito.when(pedidoService.buscarPedidos(null, null, null, null, null)).thenReturn(pagina(pedido));

        mockMvc.perform(get("/api/pedidos"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].id").value("pedido-1"));
    }

    private static org.springframework.data.domain.Page<Pedido> pagina(Pedido... pedidos) {
        return new org.springframework.data.domain.PageImpl<>(java.util.List.of(pedidos));
    }

    @Test
    void testSearch_PasaPageYSize() throws Exception {
        Mockito.when(pedidoService.buscarPedidos(null, null, null, 2, 10)).thenReturn(pagina(pedido));

        mockMvc.perform(get("/api/pedidos").param("page", "2").param("size", "10"))
                .andExpect(status().isOk());
        Mockito.verify(pedidoService).buscarPedidos(null, null, null, 2, 10);
    }

    @Test
    void testSearch_ParametroInvalido_ShouldReturn400() throws Exception {
        Mockito.when(pedidoService.buscarPedidos(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(500)))
                .thenThrow(new IllegalArgumentException("size debe estar entre 1 y 100"));

        mockMvc.perform(get("/api/pedidos").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("size debe estar entre 1 y 100"));
    }

    @Test
    void testGetById() throws Exception {
        Mockito.when(pedidoService.getPedidoById("pedido-1")).thenReturn(pedido);

        mockMvc.perform(get("/api/pedidos/pedido-1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value("pedido-1"))
                .andExpect(jsonPath("$.total").value(2000));
    }

    @Test
    void testGetById_NotFound() throws Exception {
        Mockito.when(pedidoService.getPedidoById("not-found"))
                .thenThrow(new PedidoNotFoundException("not-found"));

        mockMvc.perform(get("/api/pedidos/not-found"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testSearch() throws Exception {
        Mockito.when(pedidoService.buscarPedidos(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(pagina(pedido));

        mockMvc.perform(get("/api/pedidos/search")
                .param("estado", "ENTREGADO")
                .param("clienteId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("pedido-1"));
    }

    @Test
    void testUpdateEstado() throws Exception {
        EstadoUpdateRequest request = new EstadoUpdateRequest();
        request.setEstado(EstadoPedido.ENTREGADO);

        Pedido updated = new Pedido();
        updated.setId("pedido-1");
        updated.setEstado(EstadoPedido.ENTREGADO);

        Mockito.when(pedidoService.updateEstado(
                Mockito.eq("pedido-1"), Mockito.eq(EstadoPedido.ENTREGADO), Mockito.anyString()))
                .thenReturn(updated);

        mockMvc.perform(patch("/api/pedidos/pedido-1/estado")
                .header("X-User-Rol", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENTREGADO"));
    }

    @Test
    void testUpdateEstado_NotFound() throws Exception {
        EstadoUpdateRequest request = new EstadoUpdateRequest();
        request.setEstado(EstadoPedido.ENTREGADO);

        Mockito.when(pedidoService.updateEstado(
                Mockito.eq("not-found"), Mockito.eq(EstadoPedido.ENTREGADO), Mockito.anyString()))
                .thenThrow(new PedidoNotFoundException("not-found"));

        mockMvc.perform(patch("/api/pedidos/not-found/estado")
                .header("X-User-Rol", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void testUpdateEstado_EstadoNull_ShouldReturnBadRequest() throws Exception {
        // Body sin campo "estado" → @NotNull en EstadoUpdateRequest
        mockMvc.perform(patch("/api/pedidos/pedido-1/estado")
                .header("X-User-Rol", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testDelete() throws Exception {
        Mockito.when(pedidoService.getPedidoById("pedido-1")).thenReturn(pedido);
        Mockito.doNothing().when(pedidoService).deletePedido(Mockito.eq("pedido-1"), Mockito.anyString());

        mockMvc.perform(delete("/api/pedidos/pedido-1")
                .header("X-User-Rol", "ADMIN"))
                .andExpect(status().isNoContent());
    }

    @Test
    void testDelete_NotFound() throws Exception {
        Mockito.when(pedidoService.getPedidoById("not-found")).thenThrow(new isi.dan.ms.pedidos.exception.PedidoNotFoundException("not-found"));

        mockMvc.perform(delete("/api/pedidos/not-found")
                .header("X-User-Rol", "ADMIN"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetAll_Cliente_UsesOwnClienteId() throws Exception {
        Mockito.when(pedidoService.buscarPedidos(null, null, 7, null, null))
                .thenReturn(pagina(pedido));

        mockMvc.perform(get("/api/pedidos")
                .header("X-User-Rol", "CLIENTE")
                .header("X-User-ClienteId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void testSearch_Cliente_ForcesOwnClienteId() throws Exception {
        Mockito.when(pedidoService.buscarPedidos(null, EstadoPedido.ENTREGADO, 7, null, null))
                .thenReturn(pagina(pedido));

        mockMvc.perform(get("/api/pedidos/search")
                .param("estado", "ENTREGADO")
                .param("clienteId", "99")
                .header("X-User-Rol", "CLIENTE")
                .header("X-User-ClienteId", "7"))
                .andExpect(status().isOk());

        Mockito.verify(pedidoService).buscarPedidos(null, EstadoPedido.ENTREGADO, 7, null, null);
    }

    @Test
    void testGetById_ClienteOther_ShouldReturnForbidden() throws Exception {
        Mockito.when(pedidoService.getPedidoById("pedido-1")).thenReturn(pedido);

        mockMvc.perform(get("/api/pedidos/pedido-1")
                .header("X-User-Rol", "CLIENTE")
                .header("X-User-ClienteId", "7"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testRegistrarEntrega_Admin_ShouldReturnUpdatedPedido() throws Exception {
        isi.dan.ms.pedidos.dto.EntregaRequest request = new isi.dan.ms.pedidos.dto.EntregaRequest();
        request.setReceptor("Juan Obra");
        isi.dan.ms.pedidos.dto.EntregaItemRequest item = new isi.dan.ms.pedidos.dto.EntregaItemRequest();
        item.setLineaId("linea-1");
        item.setCantidad(BigDecimal.valueOf(1));
        request.setItems(Collections.singletonList(item));

        Pedido conEntrega = new Pedido();
        conEntrega.setId("pedido-1");
        conEntrega.setEstado(EstadoPedido.ENTREGA_PARCIAL);

        Mockito.when(pedidoService.registrarEntrega(
                Mockito.eq("pedido-1"), Mockito.any(), Mockito.anyString()))
                .thenReturn(conEntrega);

        mockMvc.perform(post("/api/pedidos/pedido-1/entregas")
                .header("X-User-Rol", "ADMIN")
                .header("X-User-Nombre", "Deposito")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENTREGA_PARCIAL"));
    }

    @Test
    void testReintentarReserva_Admin_ShouldReturnUpdatedPedido() throws Exception {
        Pedido enPreparacion = new Pedido();
        enPreparacion.setId("pedido-1");
        enPreparacion.setEstado(EstadoPedido.EN_PREPARACION);
        Mockito.when(pedidoService.reintentarReserva(Mockito.eq("pedido-1"), Mockito.anyString()))
                .thenReturn(enPreparacion);

        mockMvc.perform(post("/api/pedidos/pedido-1/reintentar-reserva")
                .header("X-User-Rol", "ADMIN")
                .header("X-User-Nombre", "Admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_PREPARACION"));
    }

    @Test
    void testReintentarReserva_Cliente_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(post("/api/pedidos/pedido-1/reintentar-reserva")
                .header("X-User-Rol", "CLIENTE")
                .header("X-User-ClienteId", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testRegistrarEntrega_Cliente_ShouldReturnForbidden() throws Exception {
        isi.dan.ms.pedidos.dto.EntregaRequest request = new isi.dan.ms.pedidos.dto.EntregaRequest();
        request.setReceptor("Juan Obra");
        isi.dan.ms.pedidos.dto.EntregaItemRequest item = new isi.dan.ms.pedidos.dto.EntregaItemRequest();
        item.setLineaId("linea-1");
        item.setCantidad(BigDecimal.ONE);
        request.setItems(Collections.singletonList(item));

        mockMvc.perform(post("/api/pedidos/pedido-1/entregas")
                .header("X-User-Rol", "CLIENTE")
                .header("X-User-ClienteId", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetEntregas_ClienteOther_ShouldReturnForbidden() throws Exception {
        Mockito.when(pedidoService.getPedidoById("pedido-1")).thenReturn(pedido);

        mockMvc.perform(get("/api/pedidos/pedido-1/entregas")
                .header("X-User-Rol", "CLIENTE")
                .header("X-User-ClienteId", "7"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetPedidoPdf_Admin_ShouldReturnPdf() throws Exception {
        Mockito.when(pedidoService.getPedidoById("pedido-1")).thenReturn(pedido);
        Mockito.when(pedidoPdfService.generarPedido(pedido)).thenReturn("%PDF-test".getBytes());

        mockMvc.perform(get("/api/pedidos/pedido-1/pdf")
                .header("X-User-Rol", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"pedido-1001.pdf\""));
    }

    private static String asJsonString(final Object obj) {
        try {
            return new ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String requestJson(Integer clienteId, Integer obraId, BigDecimal cantidad) {
        CrearPedidoRequest.Linea linea = new CrearPedidoRequest.Linea();
        linea.setProductoId(1L);
        linea.setCantidad(cantidad);
        CrearPedidoRequest request = new CrearPedidoRequest();
        request.setClienteId(clienteId);
        request.setObraId(obraId);
        request.setObservaciones("Entregar a la manana");
        request.setLineas(List.of(linea));
        return asJsonString(request);
    }

    @Test
    void testCreate_Admin_ShouldBuildPedidoFromRequest() throws Exception {
        Mockito.when(pedidoService.savePedido(
                Mockito.any(Pedido.class), Mockito.any(), Mockito.any(), Mockito.anyString()))
                .thenReturn(pedido);

        mockMvc.perform(post("/api/pedidos")
                .header("X-User-Rol", "ADMIN")
                .header("X-User-Nombre", "Admin Test")
                .header("Idempotency-Key", "key-test")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(5, 3, BigDecimal.TEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("pedido-1"));

        ArgumentCaptor<Pedido> captor = ArgumentCaptor.forClass(Pedido.class);
        Mockito.verify(pedidoService).savePedido(
                captor.capture(), Mockito.eq("ADMIN"), Mockito.eq("key-test"), Mockito.eq("Admin Test"));
        Pedido enviado = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(5, enviado.getCliente().getId());
        org.junit.jupiter.api.Assertions.assertEquals(3, enviado.getObra().getId());
        org.junit.jupiter.api.Assertions.assertEquals("Entregar a la manana", enviado.getObservaciones());
        org.junit.jupiter.api.Assertions.assertEquals(1L, enviado.getDetalle().get(0).getProducto().getId());
        org.junit.jupiter.api.Assertions.assertEquals(0, BigDecimal.TEN.compareTo(enviado.getDetalle().get(0).getCantidad()));
    }

    @Test
    void testCreate_Cliente_ShouldUseClienteIdFromTokenAndIgnoreBody() throws Exception {
        Mockito.when(pedidoService.savePedido(
                Mockito.any(Pedido.class), Mockito.any(), Mockito.any(), Mockito.anyString()))
                .thenReturn(pedido);

        mockMvc.perform(post("/api/pedidos")
                .header("X-User-Rol", "CLIENTE")
                .header("X-User-ClienteId", "7")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(99, 3, BigDecimal.ONE)))
                .andExpect(status().isOk());

        ArgumentCaptor<Pedido> captor = ArgumentCaptor.forClass(Pedido.class);
        Mockito.verify(pedidoService).savePedido(
                captor.capture(), Mockito.eq("CLIENTE"), Mockito.isNull(), Mockito.anyString());
        org.junit.jupiter.api.Assertions.assertEquals(7, captor.getValue().getCliente().getId());
    }

    @Test
    void testCreate_CamposInyectados_ShouldBeIgnored() throws Exception {
        Mockito.when(pedidoService.savePedido(
                Mockito.any(Pedido.class), Mockito.any(), Mockito.any(), Mockito.anyString()))
                .thenReturn(pedido);
        String json = "{\"obraId\":3,\"lineas\":[{\"productoId\":1,\"cantidad\":2,\"precioUnitario\":1}],"
                + "\"estado\":\"ENTREGADO\",\"total\":1,\"habilitado\":false,\"usuario\":\"hacker\","
                + "\"entregas\":[{\"receptor\":\"x\"}],\"auditoria\":[{\"accion\":\"x\"}],"
                + "\"cliente\":{\"id\":9,\"nombre\":\"Falso\",\"cuit\":\"0\"}}";

        mockMvc.perform(post("/api/pedidos")
                .header("X-User-Rol", "CLIENTE")
                .header("X-User-ClienteId", "7")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk());

        ArgumentCaptor<Pedido> captor = ArgumentCaptor.forClass(Pedido.class);
        Mockito.verify(pedidoService).savePedido(captor.capture(), Mockito.any(), Mockito.any(), Mockito.anyString());
        Pedido enviado = captor.getValue();
        org.junit.jupiter.api.Assertions.assertNull(enviado.getEstado());
        org.junit.jupiter.api.Assertions.assertNull(enviado.getTotal());
        org.junit.jupiter.api.Assertions.assertNull(enviado.getUsuario());
        org.junit.jupiter.api.Assertions.assertTrue(enviado.isHabilitado());
        org.junit.jupiter.api.Assertions.assertTrue(enviado.getEntregas().isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(enviado.getAuditoria().isEmpty());
        org.junit.jupiter.api.Assertions.assertNull(enviado.getCliente().getNombre());
        org.junit.jupiter.api.Assertions.assertNull(enviado.getDetalle().get(0).getPrecioUnitario());
    }

    @Test
    void testCreate_LineasVacias_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/pedidos")
                .header("X-User-Rol", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clienteId\":1,\"obraId\":3,\"lineas\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreate_CantidadNoPositiva_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/pedidos")
                .header("X-User-Rol", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(1, 3, BigDecimal.ZERO)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreate_SinObra_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/pedidos")
                .header("X-User-Rol", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(1, null, BigDecimal.ONE)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreate_AdminSinCliente_ShouldReturnUnprocessable() throws Exception {
        mockMvc.perform(post("/api/pedidos")
                .header("X-User-Rol", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(null, 3, BigDecimal.ONE)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void testCreate_ClienteSinClienteIdEnToken_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(post("/api/pedidos")
                .header("X-User-Rol", "CLIENTE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(null, 3, BigDecimal.ONE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testCreate_RolSinPermiso_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(post("/api/pedidos")
                .header("X-User-Rol", "GUEST")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(1, 3, BigDecimal.ONE)))
                .andExpect(status().isForbidden());
    }
}
