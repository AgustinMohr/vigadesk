package isi.dan.ms.pedidos.controller;

import com.fasterxml.jackson.databind.ObjectMapper;

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
        Mockito.when(pedidoService.getAllPedidos()).thenReturn(Collections.singletonList(pedido));

        mockMvc.perform(get("/api/pedidos"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value("pedido-1"));
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
    void testCreate() throws Exception {
        Mockito.when(pedidoService.savePedido(
                Mockito.any(Pedido.class), Mockito.any(), Mockito.any(), Mockito.anyString()))
                .thenReturn(pedido);

        mockMvc.perform(post("/api/pedidos")
                .header("X-User-Rol", "ADMIN")
                .header("X-User-Nombre", "Admin Test")
                .header("Idempotency-Key", "key-test")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(pedido)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("pedido-1"))
                .andExpect(jsonPath("$.total").value(2000));
    }

    @Test
    void testCreate_DetalleVacio_ShouldReturnBadRequest() throws Exception {
        Pedido invalido = new Pedido();
        invalido.setCliente(pedido.getCliente());
        invalido.setDetalle(Collections.emptyList());
        // numeroPedido null y detalle vacío → @NotNull / @NotEmpty

        mockMvc.perform(post("/api/pedidos")
                .header("X-User-Rol", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(invalido)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testSearch() throws Exception {
        Mockito.when(pedidoService.searchPedidos(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(Collections.singletonList(pedido));

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
        Mockito.when(pedidoService.searchPedidos(null, null, 7))
                .thenReturn(Collections.singletonList(pedido));

        mockMvc.perform(get("/api/pedidos")
                .header("X-User-Rol", "CLIENTE")
                .header("X-User-ClienteId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void testSearch_Cliente_ForcesOwnClienteId() throws Exception {
        Mockito.when(pedidoService.searchPedidos(null, EstadoPedido.ENTREGADO, 7))
                .thenReturn(Collections.singletonList(pedido));

        mockMvc.perform(get("/api/pedidos/search")
                .param("estado", "ENTREGADO")
                .param("clienteId", "99")
                .header("X-User-Rol", "CLIENTE")
                .header("X-User-ClienteId", "7"))
                .andExpect(status().isOk());

        Mockito.verify(pedidoService).searchPedidos(null, EstadoPedido.ENTREGADO, 7);
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
    void testCreate_Cliente_OverridesClienteId() throws Exception {
        Mockito.when(pedidoService.savePedido(
                Mockito.any(Pedido.class), Mockito.any(), Mockito.any(), Mockito.anyString()))
                .thenReturn(pedido);

        Pedido body = new Pedido();
        Cliente otro = new Cliente();
        otro.setId(99);
        body.setCliente(otro);
        body.setNumeroPedido(1);
        body.setTotal(BigDecimal.valueOf(100));
        body.setEstado(EstadoPedido.RECIBIDO);
        body.setDetalle(pedido.getDetalle());

        mockMvc.perform(post("/api/pedidos")
                .header("X-User-Rol", "CLIENTE")
                .header("X-User-ClienteId", "7")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(body)))
                .andExpect(status().isOk());

        ArgumentCaptor<Pedido> captor = ArgumentCaptor.forClass(Pedido.class);
        Mockito.verify(pedidoService).savePedido(
                captor.capture(), Mockito.eq("CLIENTE"), Mockito.isNull(), Mockito.anyString());
        org.junit.jupiter.api.Assertions.assertEquals(7, captor.getValue().getCliente().getId());
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
}
