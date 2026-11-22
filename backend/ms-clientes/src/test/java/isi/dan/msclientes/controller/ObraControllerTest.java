package isi.dan.msclientes.controller;

import com.fasterxml.jackson.databind.ObjectMapper;

import isi.dan.msclientes.model.EstadoObra;
import isi.dan.msclientes.model.Obra;
import isi.dan.msclientes.servicios.ObraService;

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
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ObraController.class)
public class ObraControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ObraService obraService;

    private Obra obra;

    @BeforeEach
    void setUp() {
        obra = new Obra();
        obra.setId(1);
        obra.setDireccion("Direccion Test Obra");
        obra.setPresupuesto(BigDecimal.valueOf(100));
    }

    @Test
    void testGetAll() throws Exception {
        Mockito.when(obraService.findAll()).thenReturn(Collections.singletonList(obra));

        mockMvc.perform(get("/api/obras"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].direccion").value("Direccion Test Obra"));
    }

    @Test
    void testGetById() throws Exception {
        Mockito.when(obraService.findById(1)).thenReturn(Optional.of(obra));

        mockMvc.perform(get("/api/obras/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.direccion").value("Direccion Test Obra"));
    }

    @Test
    void testCreate() throws Exception {
        Mockito.when(obraService.save(Mockito.any(Obra.class))).thenReturn(obra);

        mockMvc.perform(post("/api/obras")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(obra)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.direccion").value("Direccion Test Obra"));
    }

    @Test
    void testUpdate() throws Exception {
        Mockito.when(obraService.findById(1)).thenReturn(Optional.of(obra));
        Mockito.when(obraService.update(Mockito.any(Obra.class))).thenReturn(obra);

        mockMvc.perform(put("/api/obras/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(obra)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.direccion").value("Direccion Test Obra"));
    }

    @Test
    void testDelete() throws Exception {
        Mockito.when(obraService.findById(1)).thenReturn(Optional.of(obra));
        Mockito.doNothing().when(obraService).deleteById(1);

        mockMvc.perform(delete("/api/obras/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void testGetSinAsignar() throws Exception {
        Mockito.when(obraService.findByClienteIsNull()).thenReturn(Collections.singletonList(obra));

        mockMvc.perform(get("/api/obras/sin-asignar"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].direccion").value("Direccion Test Obra"));
    }

    @Test
    void testGetByClienteId() throws Exception {
        Mockito.when(obraService.findByClienteId(1)).thenReturn(Collections.singletonList(obra));

        mockMvc.perform(get("/api/obras/by-cliente/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].direccion").value("Direccion Test Obra"));
    }

    @Test
    void testAsignarCliente_Ok() throws Exception {
        Mockito.when(obraService.asignarCliente(1, 2)).thenReturn(obra);

        mockMvc.perform(put("/api/obras/1/asignar-cliente/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.direccion").value("Direccion Test Obra"));
    }

    @Test
    void testAsignarCliente_ObraInexistente_ShouldReturnNotFound() throws Exception {
        Mockito.when(obraService.asignarCliente(99, 1)).thenThrow(new isi.dan.msclientes.exception.ObraNotFoundException("Obra 99 no encontrada"));

        mockMvc.perform(put("/api/obras/99/asignar-cliente/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCambiarEstado_Ok() throws Exception {
        obra.setEstado(EstadoObra.FINALIZADA);
        Mockito.when(obraService.cambiarEstado(1, EstadoObra.FINALIZADA)).thenReturn(obra);

        mockMvc.perform(patch("/api/obras/1/estado")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\": \"FINALIZADA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("FINALIZADA"));
    }

    @Test
    void testCambiarEstado_InvalidEstado() throws Exception {
        mockMvc.perform(patch("/api/obras/1/estado")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\": \"INEXISTENTE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCambiarEstado_ReglaDeNegocio_ShouldReturnUnprocessable() throws Exception {
        Mockito.when(obraService.cambiarEstado(1, EstadoObra.HABILITADA))
                .thenThrow(new dan.common.error.SolicitudNoProcesableException("No se puede habilitar"));

        mockMvc.perform(patch("/api/obras/1/estado")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\": \"HABILITADA\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void testGetById_NotFound() throws Exception {
        Mockito.when(obraService.findById(99)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/obras/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testUpdate_NotFound() throws Exception {
        Mockito.when(obraService.findById(99)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/obras/99")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(obra)))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCreate_ClienteInexistente_ShouldReturnNotFound() throws Exception {
        Mockito.when(obraService.save(Mockito.any(Obra.class)))
                .thenThrow(new isi.dan.msclientes.exception.ClienteNotFoundException("Cliente 99 no encontrado"));

        mockMvc.perform(post("/api/obras")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(obra)))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCreate_PresupuestoNull_ShouldReturnBadRequest() throws Exception {
        Obra invalida = new Obra();
        invalida.setDireccion("Obra sin presupuesto");
        // presupuesto null → @NotNull

        mockMvc.perform(post("/api/obras")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(invalida)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreate_PresupuestoMenorQueMinimo_ShouldReturnBadRequest() throws Exception {
        Obra invalida = new Obra();
        invalida.setDireccion("Obra barata");
        invalida.setPresupuesto(BigDecimal.valueOf(99)); // @Min(100)

        mockMvc.perform(post("/api/obras")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(invalida)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCambiarEstado_PENDIENTE() throws Exception {
        obra.setEstado(EstadoObra.PENDIENTE);
        Mockito.when(obraService.cambiarEstado(1, EstadoObra.PENDIENTE)).thenReturn(obra);

        mockMvc.perform(patch("/api/obras/1/estado")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\": \"PENDIENTE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    void testCambiarEstado_HABILITADA() throws Exception {
        obra.setEstado(EstadoObra.HABILITADA);
        Mockito.when(obraService.cambiarEstado(1, EstadoObra.HABILITADA)).thenReturn(obra);

        mockMvc.perform(patch("/api/obras/1/estado")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\": \"HABILITADA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("HABILITADA"));
    }

    @Test
    void testCambiarEstado_EmptyBody() throws Exception {
        mockMvc.perform(patch("/api/obras/1/estado")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testDelete_NotFound() throws Exception {
        Mockito.when(obraService.findById(99)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/obras/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetAll_Cliente_FiltersByOwnClienteId() throws Exception {
        Mockito.when(obraService.findByClienteId(7)).thenReturn(Collections.singletonList(obra));

        mockMvc.perform(get("/api/obras")
                .header("X-User-Rol", "CLIENTE")
                .header("X-User-ClienteId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void testGetByClienteId_ClienteOther_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/obras/by-cliente/8")
                .header("X-User-Rol", "CLIENTE")
                .header("X-User-ClienteId", "7"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetSinAsignar_Cliente_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/obras/sin-asignar")
                .header("X-User-Rol", "CLIENTE")
                .header("X-User-ClienteId", "7"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetById_ClienteUnassigned_ShouldReturnForbidden() throws Exception {
        Mockito.when(obraService.findById(1)).thenReturn(Optional.of(obra));

        mockMvc.perform(get("/api/obras/1")
                .header("X-User-Rol", "CLIENTE")
                .header("X-User-ClienteId", "7"))
                .andExpect(status().isForbidden());
    }

    private static String asJsonString(final Object obj) {
        try {
            return new ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

