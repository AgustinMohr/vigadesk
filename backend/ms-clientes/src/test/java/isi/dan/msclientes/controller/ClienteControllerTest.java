package isi.dan.msclientes.controller;

import com.fasterxml.jackson.databind.ObjectMapper;

import isi.dan.msclientes.dto.ContextoPedidoDTO;
import isi.dan.msclientes.model.Cliente;
import isi.dan.msclientes.servicios.ClienteImportService;
import isi.dan.msclientes.servicios.ClienteService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClienteController.class)
public class ClienteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClienteService clienteService;

    @MockBean
    private ClienteImportService clienteImportService;

    private Cliente cliente;

    @BeforeEach
    void setUp() {
        cliente = new Cliente();
        cliente.setId(1);
        cliente.setNombre("Test Cliente");
        cliente.setCorreoElectronico("test@cliente.com");
        cliente.setCuit("12998887776");
        cliente.setMaximoDescubierto(BigDecimal.valueOf(100000));
    }

    @Test
    void testGetAll() throws Exception {
        Mockito.when(clienteService.findAll(null, null)).thenReturn(new PageImpl<>(Collections.singletonList(cliente)));

        mockMvc.perform(get("/api/clientes"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].nombre").value("Test Cliente"));
    }

    @Test
    void testGetAll_TamanioInvalido_ShouldReturn400() throws Exception {
        Mockito.when(clienteService.findAll(null, 500))
                .thenThrow(new IllegalArgumentException("size debe estar entre 1 y 100"));

        mockMvc.perform(get("/api/clientes").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("size debe estar entre 1 y 100"));
    }

    @Test
    void testGetById() throws Exception {
        Mockito.when(clienteService.findById(1)).thenReturn(Optional.of(cliente));

        mockMvc.perform(get("/api/clientes/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.nombre").value("Test Cliente"))
                .andExpect(jsonPath("$.cuit").value("12998887776"));
    }
    @Test
    void testGetById_NotFound() throws Exception {
        Mockito.when(clienteService.findById(2)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/clientes/2"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCreate() throws Exception {
        Mockito.when(clienteService.save(Mockito.any(Cliente.class))).thenReturn(cliente);

        mockMvc.perform(post("/api/clientes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(cliente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Test Cliente"));
    }

    @Test
    void testCreate_EmailInvalido_ShouldReturnBadRequest() throws Exception {
        Cliente invalido = new Cliente();
        invalido.setNombre("Email Invalido");
        invalido.setCorreoElectronico("no-es-un-email");

        mockMvc.perform(post("/api/clientes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(invalido)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreate_MaximoDescubiertoNegativo_ShouldReturnBadRequest() throws Exception {
        Cliente invalido = new Cliente();
        invalido.setNombre("Maximo Negativo");
        invalido.setCorreoElectronico("valido@cliente.com");
        invalido.setMaximoDescubierto(BigDecimal.valueOf(-100));

        mockMvc.perform(post("/api/clientes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(invalido)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testUpdate() throws Exception {
        Mockito.when(clienteService.findById(1)).thenReturn(Optional.of(cliente));
        Mockito.when(clienteService.update(Mockito.any(Cliente.class), Mockito.any(Cliente.class))).thenReturn(cliente);

        mockMvc.perform(put("/api/clientes/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(cliente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Test Cliente"));
    }

    @Test
    void testDelete() throws Exception {
        Mockito.when(clienteService.findById(1)).thenReturn(Optional.of(cliente));
        Mockito.doNothing().when(clienteService).deleteById(1);

        mockMvc.perform(delete("/api/clientes/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void testCheckSaldo_Ok() throws Exception {
        Mockito.when(clienteService.findById(1)).thenReturn(Optional.of(cliente));
        Mockito.when(clienteService.checkSaldo(1, BigDecimal.valueOf(5000))).thenReturn(true);

        mockMvc.perform(get("/api/clientes/1/saldo?monto=5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(true));
    }

    @Test
    void testCheckSaldo_NotFound() throws Exception {
        Mockito.when(clienteService.findById(99)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/clientes/99/saldo?monto=5000"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetMaximoDescubierto_Ok() throws Exception {
        Mockito.when(clienteService.findById(1)).thenReturn(Optional.of(cliente));
        Mockito.when(clienteService.getMaximoDescubierto(1)).thenReturn(BigDecimal.valueOf(100000));

        mockMvc.perform(get("/api/clientes/1/maximo-descubierto"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(100000));
    }

    @Test
    void testGetMaximoDescubierto_NotFound() throws Exception {
        Mockito.when(clienteService.findById(99)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/clientes/99/maximo-descubierto"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testUpdate_NotFound() throws Exception {
        Mockito.when(clienteService.findById(99)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/clientes/99")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(cliente)))
                .andExpect(status().isNotFound());
    }

    @Test
    void testDelete_NotFound() throws Exception {
        Mockito.when(clienteService.findById(99)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/clientes/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetAll_Cliente_ReturnsOnlyOwn() throws Exception {
        Mockito.when(clienteService.findById(1)).thenReturn(Optional.of(cliente));

        mockMvc.perform(get("/api/clientes")
                .header("X-User-Rol", "CLIENTE")
                .header("X-User-ClienteId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void testGetById_ClienteOther_ShouldReturnForbidden() throws Exception {
        Cliente otro = new Cliente();
        otro.setId(2);
        otro.setNombre("Otro Cliente");
        Mockito.when(clienteService.findById(2)).thenReturn(Optional.of(otro));

        mockMvc.perform(get("/api/clientes/2")
                .header("X-User-Rol", "CLIENTE")
                .header("X-User-ClienteId", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testCheckSaldo_ClienteOther_ShouldReturnForbidden() throws Exception {
        Cliente otro = new Cliente();
        otro.setId(2);
        Mockito.when(clienteService.findById(2)).thenReturn(Optional.of(otro));

        mockMvc.perform(get("/api/clientes/2/saldo?monto=100")
                .header("X-User-Rol", "CLIENTE")
                .header("X-User-ClienteId", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetContextoPedido_Ok() throws Exception {
        ContextoPedidoDTO dto = new ContextoPedidoDTO();
        dto.setClienteId(1);
        dto.setClienteNombre("Test");
        dto.setObraId(5);
        Mockito.when(clienteService.contextoPedido(1, 5)).thenReturn(Optional.of(dto));

        mockMvc.perform(get("/api/clientes/1/contexto-pedido?obraId=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteNombre").value("Test"))
                .andExpect(jsonPath("$.obraId").value(5));
    }

    @Test
    void testGetContextoPedido_ClienteNoExiste() throws Exception {
        Mockito.when(clienteService.contextoPedido(99, null)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/clientes/99/contexto-pedido"))
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

