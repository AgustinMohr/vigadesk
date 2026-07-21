package isi.dan.ms_productos.controller;

import com.fasterxml.jackson.databind.ObjectMapper;

import isi.dan.ms_productos.dto.DescuentoDTO;
import isi.dan.ms_productos.dto.ProvisionDTO;
import isi.dan.ms_productos.dto.StockUpdateDTO;
import isi.dan.ms_productos.exception.ProductoNotFoundException;
import isi.dan.ms_productos.modelo.Categoria;
import isi.dan.ms_productos.modelo.Producto;
import isi.dan.ms_productos.servicio.ProductoService;

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
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductoController.class)
class ProductoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductoService productoService;

    private Producto producto;

    @BeforeEach
    void setUp() {
        producto = new Producto();
        producto.setId(1L);
        producto.setNombre("Cemento Portland");
        producto.setDescripcion("Cemento para construccion");
        producto.setStockActual(100);
        producto.setStockMinimo(10);
        producto.setPrecio(BigDecimal.valueOf(5000));
        producto.setDescuentoPromocional(BigDecimal.ZERO);
        producto.setCategoria(Categoria.CEMENTOS);
    }

    @Test
    void testGetAll() throws Exception {
        Mockito.when(productoService.getAllProductos()).thenReturn(Collections.singletonList(producto));

        mockMvc.perform(get("/api/productos"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].nombre").value("Cemento Portland"));
    }

    @Test
    void testGetById() throws Exception {
        Mockito.when(productoService.getProductoById(1L)).thenReturn(producto);

        mockMvc.perform(get("/api/productos/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.nombre").value("Cemento Portland"))
                .andExpect(jsonPath("$.precio").value(5000));
    }

    @Test
    void testGetById_NotFound() throws Exception {
        Mockito.when(productoService.getProductoById(99L)).thenThrow(new ProductoNotFoundException(99L));

        mockMvc.perform(get("/api/productos/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCreate() throws Exception {
        Mockito.when(productoService.saveProducto(Mockito.any(Producto.class))).thenReturn(producto);

        mockMvc.perform(post("/api/productos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(producto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Cemento Portland"));
    }

    @Test
    void testDelete() throws Exception {
        Mockito.doNothing().when(productoService).deleteProducto(1L);

        mockMvc.perform(delete("/api/productos/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void testCheckStock_Ok() throws Exception {
        StockUpdateDTO item = new StockUpdateDTO();
        item.setIdProducto(1L);
        item.setCantidad(50);
        List<StockUpdateDTO> items = Collections.singletonList(item);

        Mockito.when(productoService.checkStock(Mockito.anyList())).thenReturn(true);

        mockMvc.perform(put("/api/productos/stock/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(items)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(true));
    }

    @Test
    void testCheckStock_Insuficiente() throws Exception {
        StockUpdateDTO item = new StockUpdateDTO();
        item.setIdProducto(1L);
        item.setCantidad(200);
        List<StockUpdateDTO> items = Collections.singletonList(item);

        Mockito.when(productoService.checkStock(Mockito.anyList())).thenReturn(false);

        mockMvc.perform(put("/api/productos/stock/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(items)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(false));
    }

    @Test
    void testProcesarProvision() throws Exception {
        ProvisionDTO provision = new ProvisionDTO();
        provision.setIdProducto(1L);
        provision.setCantidad(50);
        provision.setPrecio(BigDecimal.valueOf(5500));

        Producto updated = new Producto();
        updated.setId(1L);
        updated.setStockActual(150);
        updated.setPrecio(BigDecimal.valueOf(5500));

        Mockito.when(productoService.procesarProvision(Mockito.any(ProvisionDTO.class))).thenReturn(updated);

        mockMvc.perform(put("/api/productos/provision")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(provision)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockActual").value(150))
                .andExpect(jsonPath("$.precio").value(5500));
    }

    @Test
    void testActualizarDescuento() throws Exception {
        DescuentoDTO descuento = new DescuentoDTO();
        descuento.setDescuentoPromocional(BigDecimal.valueOf(10));

        Producto updated = new Producto();
        updated.setId(1L);
        updated.setDescuentoPromocional(BigDecimal.valueOf(10));

        Mockito.when(productoService.actualizarDescuento(Mockito.eq(1L), Mockito.any(DescuentoDTO.class))).thenReturn(updated);

        mockMvc.perform(put("/api/productos/1/descuento")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(descuento)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descuentoPromocional").value(10));
    }

    private static String asJsonString(final Object obj) {
        try {
            return new ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
