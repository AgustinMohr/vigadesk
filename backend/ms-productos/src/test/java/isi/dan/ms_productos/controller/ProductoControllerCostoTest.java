package isi.dan.ms_productos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import isi.dan.ms_productos.modelo.Categoria;
import isi.dan.ms_productos.modelo.Producto;
import isi.dan.ms_productos.servicio.ProductoImportService;
import isi.dan.ms_productos.servicio.ProductoService;

/**
 * El costo (margen del corralon) y el usuario que modifico un producto solo los ve
 * un ADMIN. Clientes e invitados reciben el catalogo sin esos datos.
 */
@WebMvcTest(ProductoController.class)
class ProductoControllerCostoTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductoService productoService;

    @MockBean
    private ProductoImportService productoImportService;

    private Producto producto;

    @BeforeEach
    void setUp() {
        producto = new Producto();
        producto.setId(1L);
        producto.setNombre("Cemento Portland");
        producto.setPrecio(BigDecimal.valueOf(5000));
        producto.setCosto(BigDecimal.valueOf(3200));
        producto.setActualizadoPor("Admin Interno");
        producto.setStockActual(BigDecimal.valueOf(100));
        producto.setStockMinimo(BigDecimal.valueOf(10));
        producto.setCategoria(Categoria.CEMENTOS);
        Mockito.when(productoService.getAllProductos()).thenReturn(Collections.singletonList(producto));
        Mockito.when(productoService.getProductoById(1L)).thenReturn(producto);
        Mockito.when(productoService.searchProductos(Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(Collections.singletonList(producto));
    }

    @Test
    void getAll_Admin_ShouldIncludeCostoAndAuthor() throws Exception {
        mockMvc.perform(get("/api/productos").header("X-User-Rol", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].costo").value(3200))
                .andExpect(jsonPath("$[0].actualizadoPor").value("Admin Interno"));
    }

    @Test
    void getAll_Cliente_ShouldOmitCostoAndAuthor() throws Exception {
        mockMvc.perform(get("/api/productos").header("X-User-Rol", "CLIENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Cemento Portland"))
                .andExpect(jsonPath("$[0].precio").value(5000))
                .andExpect(jsonPath("$[0].costo").doesNotExist())
                .andExpect(jsonPath("$[0].actualizadoPor").doesNotExist());
    }

    @Test
    void getAll_Guest_ShouldOmitCostoAndAuthor() throws Exception {
        mockMvc.perform(get("/api/productos").header("X-User-Rol", "GUEST"))
                .andExpect(jsonPath("$[0].costo").doesNotExist())
                .andExpect(jsonPath("$[0].actualizadoPor").doesNotExist());
    }

    @Test
    void getAll_WithoutRole_ShouldOmitCosto() throws Exception {
        mockMvc.perform(get("/api/productos"))
                .andExpect(jsonPath("$[0].costo").doesNotExist());
    }

    @Test
    void getById_Cliente_ShouldOmitCostoButKeepCommercialData() throws Exception {
        mockMvc.perform(get("/api/productos/1").header("X-User-Rol", "CLIENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precio").value(5000))
                .andExpect(jsonPath("$.stockActual").value(100))
                .andExpect(jsonPath("$.stockMinimo").value(10))
                .andExpect(jsonPath("$.costo").doesNotExist())
                .andExpect(jsonPath("$.actualizadoPor").doesNotExist());
    }

    @Test
    void getById_Admin_ShouldIncludeCosto() throws Exception {
        mockMvc.perform(get("/api/productos/1").header("X-User-Rol", "ADMIN"))
                .andExpect(jsonPath("$.costo").value(3200));
    }

    @Test
    void search_Cliente_ShouldOmitCosto() throws Exception {
        mockMvc.perform(get("/api/productos/search").header("X-User-Rol", "CLIENTE"))
                .andExpect(jsonPath("$[0].costo").doesNotExist())
                .andExpect(jsonPath("$[0].actualizadoPor").doesNotExist());
    }

    @Test
    void search_Admin_ShouldIncludeCosto() throws Exception {
        mockMvc.perform(get("/api/productos/search").header("X-User-Rol", "ADMIN"))
                .andExpect(jsonPath("$[0].costo").value(3200));
    }

    @Test
    void maskingMustNotMutateTheManagedEntity() throws Exception {
        mockMvc.perform(get("/api/productos").header("X-User-Rol", "CLIENTE")).andExpect(status().isOk());
        mockMvc.perform(get("/api/productos/1").header("X-User-Rol", "CLIENTE")).andExpect(status().isOk());

        // Si se modificara la entidad original, Hibernate podria persistir el null.
        assertThat(producto.getCosto()).isEqualByComparingTo(BigDecimal.valueOf(3200));
        assertThat(producto.getActualizadoPor()).isEqualTo("Admin Interno");
    }
}
