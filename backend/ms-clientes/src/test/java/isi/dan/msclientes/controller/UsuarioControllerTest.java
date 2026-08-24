package isi.dan.msclientes.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import isi.dan.msclientes.dto.LoginRequest;
import isi.dan.msclientes.dto.LoginResponse;
import isi.dan.msclientes.model.Cliente;
import isi.dan.msclientes.model.UsuarioCliente;
import isi.dan.msclientes.security.JwtService;
import isi.dan.msclientes.servicios.UsuarioService;

@WebMvcTest(UsuarioController.class)
class UsuarioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UsuarioService usuarioService;

    @MockBean
    private JwtService jwtService;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private UsuarioCliente usuarioConCliente() {
        Cliente cliente = new Cliente();
        cliente.setId(1);
        cliente.setNombre("Constructora del Sur S.A.");
        UsuarioCliente u = new UsuarioCliente();
        u.setId(1);
        u.setNombre("Juan");
        u.setEmail("juan@constructorasur.com");
        u.setPassword("juan123");
        u.setCliente(cliente);
        return u;
    }

    /**
     * Body con password explícito: el campo password es WRITE_ONLY, por lo que
     * Jackson no lo serializa y la validacion @NotBlank fallaria con 400.
     */
    private static final String CREATE_BODY =
            "{\"nombre\":\"Juan\",\"email\":\"juan@constructorasur.com\",\"password\":\"juan123\","
            + "\"cliente\":{\"id\":1,\"nombre\":\"Constructora del Sur S.A.\"}}";

    @Test
    void getAll_ShouldReturnUsuarios() throws Exception {
        Mockito.when(usuarioService.findAll()).thenReturn(Collections.singletonList(usuarioConCliente()));

        mockMvc.perform(get("/api/usuarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("juan@constructorasur.com"));
    }

    @Test
    void getById_ShouldReturnUsuario() throws Exception {
        Mockito.when(usuarioService.findById(1)).thenReturn(Optional.of(usuarioConCliente()));

        mockMvc.perform(get("/api/usuarios/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("juan@constructorasur.com"));
    }

    @Test
    void getById_NotFound_ShouldReturn404() throws Exception {
        Mockito.when(usuarioService.findById(99)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/usuarios/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_ShouldReturnUsuario() throws Exception {
        Mockito.when(usuarioService.save(Mockito.any(UsuarioCliente.class))).thenReturn(usuarioConCliente());

        mockMvc.perform(post("/api/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("juan@constructorasur.com"));
    }

    @Test
    void update_ShouldReturnUsuario() throws Exception {
        Mockito.when(usuarioService.findById(1)).thenReturn(Optional.of(usuarioConCliente()));
        Mockito.when(usuarioService.update(Mockito.any(), Mockito.any())).thenReturn(usuarioConCliente());

        mockMvc.perform(put("/api/usuarios/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void delete_ShouldReturnNoContent() throws Exception {
        Mockito.when(usuarioService.findById(1)).thenReturn(Optional.of(usuarioConCliente()));
        Mockito.doNothing().when(usuarioService).deleteById(1);

        mockMvc.perform(delete("/api/usuarios/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void login_Ok_ShouldReturnTokenAndUser() throws Exception {
        LoginResponse resp = new LoginResponse();
        resp.setToken("jwt-token");
        resp.setEmail("juan@constructorasur.com");
        resp.setNombre("Juan");
        resp.setRol("CLIENTE");
        resp.setClienteId(1);
        resp.setClienteNombre("Constructora del Sur S.A.");

        Mockito.when(usuarioService.validarLogin("juan@constructorasur.com", "juan123"))
                .thenReturn(Optional.of(usuarioConCliente()));
        Mockito.when(jwtService.generateToken(Mockito.any(UsuarioCliente.class))).thenReturn("jwt-token");

        LoginRequest login = new LoginRequest();
        login.setEmail("juan@constructorasur.com");
        login.setPassword("juan123");

        mockMvc.perform(post("/api/usuarios/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.email").value("juan@constructorasur.com"))
                .andExpect(jsonPath("$.rol").value("CLIENTE"))
                .andExpect(jsonPath("$.clienteId").value(1));
    }

    @Test
    void login_CredencialesInvalidas_ShouldReturn401() throws Exception {
        Mockito.when(usuarioService.validarLogin("juan@constructorasur.com", "mal"))
                .thenReturn(Optional.empty());

        LoginRequest login = new LoginRequest();
        login.setEmail("juan@constructorasur.com");
        login.setPassword("mal");

        mockMvc.perform(post("/api/usuarios/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(login)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_EmailInvalido_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Juan\",\"email\":\"no-es-email\",\"password\":\"x12345\",\"cliente\":{\"id\":1}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_NombreVacio_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"\",\"email\":\"juan@mail.com\",\"password\":\"x12345\",\"cliente\":{\"id\":1}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_SinPassword_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Juan\",\"email\":\"juan@mail.com\",\"cliente\":{\"id\":1}}"))
                .andExpect(status().isBadRequest());
    }
}
