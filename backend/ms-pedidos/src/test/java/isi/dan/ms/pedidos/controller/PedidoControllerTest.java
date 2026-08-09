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
import isi.dan.ms.pedidos.servicio.PedidoService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
        detalle.setCantidad(2);
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
        Mockito.when(pedidoService.savePedido(Mockito.any(Pedido.class))).thenReturn(pedido);

        mockMvc.perform(post("/api/pedidos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(pedido)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("pedido-1"))
                .andExpect(jsonPath("$.total").value(2000));
    }

    @Test
    void testUpdateEstado() throws Exception {
        EstadoUpdateRequest request = new EstadoUpdateRequest();
        request.setEstado(EstadoPedido.ENTREGADO);

        Pedido updated = new Pedido();
        updated.setId("pedido-1");
        updated.setEstado(EstadoPedido.ENTREGADO);

        Mockito.when(pedidoService.updateEstado("pedido-1", EstadoPedido.ENTREGADO)).thenReturn(updated);

        mockMvc.perform(patch("/api/pedidos/pedido-1/estado")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENTREGADO"));
    }

    @Test
    void testUpdateEstado_NotFound() throws Exception {
        EstadoUpdateRequest request = new EstadoUpdateRequest();
        request.setEstado(EstadoPedido.ENTREGADO);

        Mockito.when(pedidoService.updateEstado("not-found", EstadoPedido.ENTREGADO))
                .thenThrow(new PedidoNotFoundException("not-found"));

        mockMvc.perform(patch("/api/pedidos/not-found/estado")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void testDelete() throws Exception {
        Mockito.when(pedidoService.getPedidoById("pedido-1")).thenReturn(pedido);
        Mockito.doNothing().when(pedidoService).deletePedido("pedido-1");

        mockMvc.perform(delete("/api/pedidos/pedido-1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void testDelete_NotFound() throws Exception {
        Mockito.when(pedidoService.getPedidoById("not-found")).thenThrow(new isi.dan.ms.pedidos.exception.PedidoNotFoundException("not-found"));

        mockMvc.perform(delete("/api/pedidos/not-found"))
                .andExpect(status().isNotFound());
    }

    private static String asJsonString(final Object obj) {
        try {
            return new ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
