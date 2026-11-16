package isi.dan.msclientes.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import isi.dan.msclientes.model.Cliente;
import isi.dan.msclientes.model.Pago;
import isi.dan.msclientes.servicios.PagoService;

@WebMvcTest(PagoController.class)
class PagoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PagoService pagoService;

    private Pago pago;

    @BeforeEach
    void setUp() {
        Cliente cliente = new Cliente();
        cliente.setId(1);
        cliente.setNombre("Test Cliente");

        pago = new Pago();
        pago.setId(10);
        pago.setCliente(cliente);
        pago.setMonto(BigDecimal.valueOf(5000));
        pago.setMetodoPago("TRANSFERENCIA");
    }

    @Test
    void getAll_AdminCanReadAnyClient() throws Exception {
        when(pagoService.findByCliente(1)).thenReturn(Collections.singletonList(pago));

        mockMvc.perform(get("/api/clientes/1/pagos")
                .header("X-User-Rol", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].monto").value(5000));
    }

    @Test
    void getAll_ClienteCanReadOwnPayments() throws Exception {
        when(pagoService.findByCliente(1)).thenReturn(Collections.singletonList(pago));

        mockMvc.perform(get("/api/clientes/1/pagos")
                .header("X-User-Rol", "CLIENTE")
                .header("X-User-ClienteId", "1"))
                .andExpect(status().isOk());
    }

    @Test
    void getAll_ClienteCannotReadOtherClientPayments() throws Exception {
        mockMvc.perform(get("/api/clientes/2/pagos")
                .header("X-User-Rol", "CLIENTE")
                .header("X-User-ClienteId", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_AdminCanRegisterPayment() throws Exception {
        when(pagoService.registrar(any(), any(), anyString())).thenReturn(pago);

        mockMvc.perform(post("/api/clientes/1/pagos")
                .header("X-User-Rol", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(pago)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metodoPago").value("TRANSFERENCIA"));
    }

    @Test
    void create_ClienteCannotRegisterPayment() throws Exception {
        mockMvc.perform(post("/api/clientes/1/pagos")
                .header("X-User-Rol", "CLIENTE")
                .header("X-User-ClienteId", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(pago)))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_AdminCanDeletePayment() throws Exception {
        mockMvc.perform(delete("/api/clientes/1/pagos/10")
                .header("X-User-Rol", "ADMIN"))
                .andExpect(status().isNoContent());
    }

    private static String asJsonString(final Object obj) {
        try {
            return new ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
