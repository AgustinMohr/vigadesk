package isi.dan.msclientes.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import isi.dan.msclientes.dto.ImportResult;
import isi.dan.msclientes.servicios.ClienteImportService;
import isi.dan.msclientes.servicios.ClienteService;

/** Importacion de clientes por CSV: el controlador valida el archivo y delega en el servicio. */
@WebMvcTest(ClienteController.class)
class ClienteImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClienteService clienteService;

    @MockBean
    private ClienteImportService clienteImportService;

    private MockMultipartFile archivo(String contenido) {
        return new MockMultipartFile("file", "clientes.csv", "text/csv", contenido.getBytes());
    }

    @Test
    void importar_ShouldReturnTheResult() throws Exception {
        when(clienteImportService.importar(any(InputStream.class)))
                .thenReturn(new ImportResult(3, 0, "Clientes importados: 3 creados, 0 actualizados"));

        mockMvc.perform(multipart("/api/clientes/import").file(archivo("nombre,correoElectronico\nA,a@b.com\n")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creados").value(3))
                .andExpect(jsonPath("$.actualizados").value(0));
    }

    @Test
    void importar_ArchivoVacio_ShouldReturn400WithoutCallingTheService() throws Exception {
        mockMvc.perform(multipart("/api/clientes/import").file(archivo("")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El archivo CSV es obligatorio"));

        verify(clienteImportService, never()).importar(any());
    }

    @Test
    void importar_SinParteFile_ShouldReturn400() throws Exception {
        mockMvc.perform(multipart("/api/clientes/import"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void importar_CsvMalformado_ShouldReturn400WithTheServiceMessage() throws Exception {
        when(clienteImportService.importar(any(InputStream.class)))
                .thenThrow(new IllegalArgumentException("No se pudo leer el CSV de clientes: comillas sin cerrar"));

        mockMvc.perform(multipart("/api/clientes/import").file(archivo("nombre\n\"x\n")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No se pudo leer el CSV de clientes: comillas sin cerrar"));
    }
}
