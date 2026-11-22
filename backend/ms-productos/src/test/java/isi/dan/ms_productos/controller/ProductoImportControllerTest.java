package isi.dan.ms_productos.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import isi.dan.ms_productos.dto.ImportResult;
import isi.dan.ms_productos.servicio.ProductoImportService;
import isi.dan.ms_productos.servicio.ProductoService;

/** Importacion de productos por CSV: el controlador valida el archivo y delega en el servicio. */
@WebMvcTest(ProductoController.class)
class ProductoImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductoService productoService;

    @MockBean
    private ProductoImportService productoImportService;

    private MockMultipartFile archivo(String contenido) {
        return new MockMultipartFile("file", "productos.csv", "text/csv", contenido.getBytes());
    }

    @Test
    void importar_ShouldReturnTheResultAndPassTheActor() throws Exception {
        when(productoImportService.importar(any(InputStream.class), ArgumentMatchers.eq("Ana")))
                .thenReturn(new ImportResult(2, 1, "Productos importados: 2 creados, 1 actualizados"));

        mockMvc.perform(multipart("/api/productos/import").file(archivo("nombre,precio\nA,1\n"))
                .header("X-User-Nombre", "Ana").header("X-User-Email", "ana@mail.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creados").value(2))
                .andExpect(jsonPath("$.actualizados").value(1));
    }

    @Test
    void importar_SinCabecerasDeUsuario_ShouldUseTheSystemActor() throws Exception {
        when(productoImportService.importar(any(InputStream.class), ArgumentMatchers.eq("sistema")))
                .thenReturn(new ImportResult(1, 0, "ok"));

        mockMvc.perform(multipart("/api/productos/import").file(archivo("nombre,precio\nA,1\n")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creados").value(1));
    }

    @Test
    void importar_ArchivoVacio_ShouldReturn400WithoutCallingTheService() throws Exception {
        mockMvc.perform(multipart("/api/productos/import").file(archivo("")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El archivo CSV es obligatorio"));

        verify(productoImportService, never()).importar(any(), any());
    }

    @Test
    void importar_SinParteFile_ShouldReturn400() throws Exception {
        mockMvc.perform(multipart("/api/productos/import"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void importar_CsvMalformado_ShouldReturn400WithTheServiceMessage() throws Exception {
        when(productoImportService.importar(any(InputStream.class), any()))
                .thenThrow(new IllegalArgumentException("Fila 2: precio no es un numero valido: caro"));

        mockMvc.perform(multipart("/api/productos/import").file(archivo("nombre,precio\nA,caro\n")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Fila 2: precio no es un numero valido: caro"));
    }
}
