package isi.dan.ms_productos.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import isi.dan.ms_productos.dao.ProductoRepository;
import isi.dan.ms_productos.dto.DescuentoDTO;
import isi.dan.ms_productos.dto.ProvisionDTO;
import isi.dan.ms_productos.dto.StockUpdateDTO;
import isi.dan.ms_productos.exception.InsufficientStockException;
import isi.dan.ms_productos.exception.ProductoNotFoundException;
import isi.dan.ms_productos.modelo.Categoria;
import isi.dan.ms_productos.modelo.Producto;
import isi.dan.ms_productos.modelo.UnidadMedida;

@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {

    @Mock
    private ProductoRepository productoRepository;

    @InjectMocks
    private ProductoService productoService;

    private Producto producto;

    @BeforeEach
    void setUp() {
        producto = new Producto();
        producto.setId(1L);
        producto.setNombre("Cemento Portland");
        producto.setDescripcion("Cemento para construccion");
        producto.setStockActual(BigDecimal.valueOf(100));
        producto.setStockMinimo(BigDecimal.valueOf(10));
        producto.setPrecio(BigDecimal.valueOf(5000));
        producto.setDescuentoPromocional(BigDecimal.ZERO);
        producto.setCategoria(Categoria.CEMENTOS);
    }

    @Test
    void saveProducto_ShouldSetStockCero() {
        when(productoRepository.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));

        Producto result = productoService.saveProducto(producto);

        assertThat(result.getStockActual()).isZero();
    }

    @Test
    void saveProducto_ShouldApplyDefaultsAndAuditActor() {
        when(productoRepository.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));

        Producto result = productoService.saveProducto(producto, "Admin");

        assertThat(result.getStockActual()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getUnidadMedida()).isEqualTo(UnidadMedida.UNIDAD);
        assertThat(result.getActualizadoPor()).isEqualTo("Admin");
        assertThat(result.getFechaActualizacion()).isNotNull();
    }

    @Test
    void updateProducto_ShouldPersistUnitCostAndDecimalStockMinimum() {
        when(productoRepository.findByIdAndHabilitadoTrue(1L)).thenReturn(Optional.of(producto));
        when(productoRepository.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));

        Producto cambios = new Producto();
        cambios.setNombre("Cemento");
        cambios.setPrecio(BigDecimal.valueOf(9000));
        cambios.setStockMinimo(new BigDecimal("12.500"));
        cambios.setUnidadMedida(UnidadMedida.KG);
        cambios.setCosto(BigDecimal.valueOf(7000));
        cambios.setCategoria(Categoria.CEMENTOS);

        Producto result = productoService.updateProducto(1L, cambios, "Admin");

        assertThat(result.getUnidadMedida()).isEqualTo(UnidadMedida.KG);
        assertThat(result.getCosto()).isEqualByComparingTo(BigDecimal.valueOf(7000));
        assertThat(result.getStockMinimo()).isEqualByComparingTo(new BigDecimal("12.500"));
    }

    @Test
    void procesarProvision_ShouldSupportDecimalQuantity() {
        when(productoRepository.findByIdAndHabilitadoTrue(1L)).thenReturn(Optional.of(producto));
        when(productoRepository.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));

        ProvisionDTO provision = new ProvisionDTO();
        provision.setIdProducto(1L);
        provision.setCantidad(new BigDecimal("2.500"));

        Producto result = productoService.procesarProvision(provision, "Deposito");

        assertThat(result.getStockActual()).isEqualByComparingTo(new BigDecimal("102.500"));
    }

    @Test
    void getAllProductos_ShouldReturnAll() {
        when(productoRepository.findByHabilitadoTrue()).thenReturn(Collections.singletonList(producto));

        List<Producto> result = productoService.getAllProductos();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNombre()).isEqualTo("Cemento Portland");
    }

    @Test
    void getProductoById_WhenExists_ShouldReturnProducto() throws ProductoNotFoundException {
        when(productoRepository.findByIdAndHabilitadoTrue(1L)).thenReturn(Optional.of(producto));

        Producto result = productoService.getProductoById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getNombre()).isEqualTo("Cemento Portland");
    }

    @Test
    void getProductoById_WhenNotExists_ShouldThrowProductoNotFoundException() {
        when(productoRepository.findByIdAndHabilitadoTrue(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productoService.getProductoById(99L))
                .isInstanceOf(ProductoNotFoundException.class)
                .hasMessageContaining("Producto 99 no encontrado");
    }

    @Test
    void deleteProducto_ShouldSoftDelete() {
        when(productoRepository.findByIdAndHabilitadoTrue(1L)).thenReturn(Optional.of(producto));

        productoService.deleteProducto(1L);

        assertThat(producto.isHabilitado()).isFalse();
        assertThat(producto.getFechaBorrado()).isNotNull();
        verify(productoRepository).save(producto);
    }

    @Test
    void deleteProducto_WhenAlreadyDeleted_ShouldDoNothing() {
        when(productoRepository.findByIdAndHabilitadoTrue(1L)).thenReturn(Optional.empty());

        productoService.deleteProducto(1L);

        verify(productoRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any(Producto.class));
    }

    @Test
    void reserveStock_WhenAtomicUpdateSucceeds_ShouldReturnTrue() {
        StockUpdateDTO item = new StockUpdateDTO();
        item.setIdProducto(1L);
        item.setCantidad(BigDecimal.valueOf(50));
        when(productoRepository.descontarStock(
                eq(1L), eq(BigDecimal.valueOf(50)), anyString(), any())).thenReturn(1);

        boolean result = productoService.reserveStock(Collections.singletonList(item));

        assertThat(result).isTrue();
        verify(productoRepository).descontarStock(
                eq(1L), eq(BigDecimal.valueOf(50)), anyString(), any());
    }

    @Test
    void reserveStock_WhenStockInsufficient_ShouldThrowAndNotContinue() {
        StockUpdateDTO item = new StockUpdateDTO();
        item.setIdProducto(1L);
        item.setCantidad(BigDecimal.valueOf(50));
        when(productoRepository.descontarStock(
                eq(1L), eq(BigDecimal.valueOf(50)), anyString(), any())).thenReturn(0);

        assertThatThrownBy(() -> productoService.reserveStock(Collections.singletonList(item)))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Stock insuficiente");
    }

    @Test
    void reserveStock_WhenQuantityIsInvalid_ShouldThrow() {
        StockUpdateDTO item = new StockUpdateDTO();
        item.setIdProducto(1L);
        item.setCantidad(BigDecimal.ZERO);

        assertThatThrownBy(() -> productoService.reserveStock(Collections.singletonList(item)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void releaseStock_WhenProductExists_ShouldIncreaseStock() {
        StockUpdateDTO item = new StockUpdateDTO();
        item.setIdProducto(1L);
        item.setCantidad(BigDecimal.valueOf(5));
        when(productoRepository.reponerStock(
                eq(1L), eq(BigDecimal.valueOf(5)), anyString(), any())).thenReturn(1);

        productoService.releaseStock(Collections.singletonList(item));

        verify(productoRepository).reponerStock(
                eq(1L), eq(BigDecimal.valueOf(5)), anyString(), any());
    }

    @Test
    void releaseStock_WhenProductDoesNotExist_ShouldThrow() {
        StockUpdateDTO item = new StockUpdateDTO();
        item.setIdProducto(99L);
        item.setCantidad(BigDecimal.valueOf(5));
        when(productoRepository.reponerStock(
                eq(99L), eq(BigDecimal.valueOf(5)), anyString(), any())).thenReturn(0);

        assertThatThrownBy(() -> productoService.releaseStock(Collections.singletonList(item)))
                .isInstanceOf(ProductoNotFoundException.class);
    }

    @Test
    void checkStock_WhenAllHaveStock_ShouldReturnTrue() {
        StockUpdateDTO item = new StockUpdateDTO();
        item.setIdProducto(1L);
        item.setCantidad(BigDecimal.valueOf(50));
        when(productoRepository.findByIdAndHabilitadoTrue(1L)).thenReturn(Optional.of(producto));

        boolean result = productoService.checkStock(Collections.singletonList(item));

        assertThat(result).isTrue();
    }

    @Test
    void checkStock_WhenStockInsuficiente_ShouldReturnFalse() {
        StockUpdateDTO item = new StockUpdateDTO();
        item.setIdProducto(1L);
        item.setCantidad(BigDecimal.valueOf(200));
        when(productoRepository.findByIdAndHabilitadoTrue(1L)).thenReturn(Optional.of(producto));

        boolean result = productoService.checkStock(Collections.singletonList(item));

        assertThat(result).isFalse();
    }

    @Test
    void checkStock_WhenProductoNoExiste_ShouldThrowRuntimeException() {
        StockUpdateDTO item = new StockUpdateDTO();
        item.setIdProducto(99L);
        item.setCantidad(BigDecimal.valueOf(10));
        when(productoRepository.findByIdAndHabilitadoTrue(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productoService.checkStock(Collections.singletonList(item)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Producto no encontrado: 99");
    }

    @Test
    void checkStock_MultipleItems_AllHaveStock_ShouldReturnTrue() {
        StockUpdateDTO item1 = new StockUpdateDTO();
        item1.setIdProducto(1L);
        item1.setCantidad(BigDecimal.valueOf(30));

        Producto producto2 = new Producto();
        producto2.setId(2L);
        producto2.setStockActual(BigDecimal.valueOf(50));
        producto2.setPrecio(BigDecimal.valueOf(3000));

        StockUpdateDTO item2 = new StockUpdateDTO();
        item2.setIdProducto(2L);
        item2.setCantidad(BigDecimal.valueOf(20));

        when(productoRepository.findByIdAndHabilitadoTrue(1L)).thenReturn(Optional.of(producto));
        when(productoRepository.findByIdAndHabilitadoTrue(2L)).thenReturn(Optional.of(producto2));

        boolean result = productoService.checkStock(List.of(item1, item2));

        assertThat(result).isTrue();
    }

    @Test
    void procesarProvision_ShouldUpdateStockAndPrice() {
        ProvisionDTO provision = new ProvisionDTO();
        provision.setIdProducto(1L);
        provision.setCantidad(BigDecimal.valueOf(50));
        provision.setPrecio(BigDecimal.valueOf(5500));

        when(productoRepository.findByIdAndHabilitadoTrue(1L)).thenReturn(Optional.of(producto));
        when(productoRepository.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));

        Producto result = productoService.procesarProvision(provision);

        assertThat(result.getStockActual()).isEqualByComparingTo(BigDecimal.valueOf(150));
        assertThat(result.getPrecio()).isEqualByComparingTo(BigDecimal.valueOf(5500));
    }

    @Test
    void procesarProvision_WhenProductoNoExiste_ShouldThrowRuntimeException() {
        ProvisionDTO provision = new ProvisionDTO();
        provision.setIdProducto(99L);
        provision.setCantidad(BigDecimal.valueOf(10));
        when(productoRepository.findByIdAndHabilitadoTrue(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productoService.procesarProvision(provision))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Producto 99 no encontrado");
    }

    @Test
    void procesarProvision_CantidadInvalida_ShouldThrowIllegalArgumentException() {
        ProvisionDTO provision = new ProvisionDTO();
        provision.setIdProducto(1L);
        provision.setCantidad(BigDecimal.ZERO);

        assertThatThrownBy(() -> productoService.procesarProvision(provision))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("La cantidad a reponer debe ser mayor a cero");
    }

    @Test
    void procesarProvision_CantidadNull_ShouldThrowIllegalArgumentException() {
        ProvisionDTO provision = new ProvisionDTO();
        provision.setIdProducto(1L);

        assertThatThrownBy(() -> productoService.procesarProvision(provision))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("La cantidad a reponer debe ser mayor a cero");
    }

    @Test
    void procesarProvision_PrecioNegativo_ShouldThrowIllegalArgumentException() {
        ProvisionDTO provision = new ProvisionDTO();
        provision.setIdProducto(1L);
        provision.setCantidad(BigDecimal.valueOf(10));
        provision.setPrecio(BigDecimal.valueOf(-5));

        assertThatThrownBy(() -> productoService.procesarProvision(provision))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("El precio debe ser mayor o igual a 0");
    }

    @Test
    void actualizarDescuento_ShouldUpdateDescuento() {
        DescuentoDTO descuento = new DescuentoDTO();
        descuento.setDescuentoPromocional(BigDecimal.valueOf(10));

        when(productoRepository.findByIdAndHabilitadoTrue(1L)).thenReturn(Optional.of(producto));
        when(productoRepository.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));

        Producto result = productoService.actualizarDescuento(1L, descuento);

        assertThat(result.getDescuentoPromocional()).isEqualByComparingTo(BigDecimal.valueOf(10));
    }

    @Test
    void actualizarDescuento_WhenProductoNoExiste_ShouldThrowRuntimeException() {
        DescuentoDTO descuento = new DescuentoDTO();
        descuento.setDescuentoPromocional(BigDecimal.valueOf(10));
        when(productoRepository.findByIdAndHabilitadoTrue(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productoService.actualizarDescuento(99L, descuento))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Producto no encontrado: 99");
    }

    @Test
    void actualizarDescuento_DiscountAbove100_ShouldThrow() {
        DescuentoDTO descuento = new DescuentoDTO();
        descuento.setDescuentoPromocional(BigDecimal.valueOf(150));

        assertThatThrownBy(() -> productoService.actualizarDescuento(1L, descuento))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("menor o igual a 100");
    }

    @Test
    void actualizarDescuento_DiscountNegative_ShouldThrow() {
        DescuentoDTO descuento = new DescuentoDTO();
        descuento.setDescuentoPromocional(BigDecimal.valueOf(-5));

        assertThatThrownBy(() -> productoService.actualizarDescuento(1L, descuento))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mayor o igual a 0");
    }

    @Test
    void actualizarDescuento_DiscountNull_ShouldThrow() {
        DescuentoDTO descuento = new DescuentoDTO();

        assertThatThrownBy(() -> productoService.actualizarDescuento(1L, descuento))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("obligatorio");
    }

    @Test
    void saveProducto_NullDiscount_ShouldDefaultToZero() {
        producto.setDescuentoPromocional(null);
        when(productoRepository.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));

        Producto result = productoService.saveProducto(producto);

        assertThat(result.getDescuentoPromocional()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void updateProducto_NullDiscount_ShouldPreserveExistingDiscount() {
        producto.setDescuentoPromocional(BigDecimal.valueOf(15));
        Producto body = new Producto();
        body.setNombre("Cemento Premium");
        body.setPrecio(BigDecimal.valueOf(6000));
        body.setStockMinimo(BigDecimal.valueOf(5));
        body.setDescuentoPromocional(null);

        when(productoRepository.findByIdAndHabilitadoTrue(1L)).thenReturn(Optional.of(producto));
        when(productoRepository.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));

        Producto result = productoService.updateProducto(1L, body);

        assertThat(result.getDescuentoPromocional()).isEqualByComparingTo(BigDecimal.valueOf(15));
    }

    @Test
    void updateProducto_DiscountAbove100_ShouldThrow() {
        producto.setDescuentoPromocional(BigDecimal.ZERO);
        Producto body = new Producto();
        body.setNombre("Cemento Premium");
        body.setPrecio(BigDecimal.valueOf(6000));
        body.setDescuentoPromocional(BigDecimal.valueOf(150));

        when(productoRepository.findByIdAndHabilitadoTrue(1L)).thenReturn(Optional.of(producto));

        assertThatThrownBy(() -> productoService.updateProducto(1L, body))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("menor o igual a 100");
    }

    @Test
    void searchProductos_NoFilters_ShouldReturnAllHabilitados() {
        when(productoRepository.findAll(any(Specification.class)))
                .thenReturn(Collections.singletonList(producto));

        List<Producto> result = productoService.searchProductos(null, null, null, null, null, null);

        assertThat(result).hasSize(1);
    }

    @Test
    void searchProductos_ByNombreYCodigo_ShouldFilter() {
        when(productoRepository.findAll(any(Specification.class)))
                .thenReturn(Collections.singletonList(producto));

        List<Producto> result = productoService.searchProductos("cemento", "C-1", null, null, null, null);

        assertThat(result).hasSize(1);
    }

    @Test
    void searchProductos_ByRangoDePrecioYStock_ShouldFilter() {
        when(productoRepository.findAll(any(Specification.class)))
                .thenReturn(Collections.singletonList(producto));

        List<Producto> result = productoService.searchProductos(
                null, null, BigDecimal.valueOf(100), BigDecimal.valueOf(10000),
                BigDecimal.valueOf(5), BigDecimal.valueOf(200));

        assertThat(result).hasSize(1);
    }

    @Test
    void searchProductos_WithBlankFilters_ShouldIgnoreBlank() {
        when(productoRepository.findAll(any(Specification.class)))
                .thenReturn(Collections.singletonList(producto));

        List<Producto> result = productoService.searchProductos("  ", "  ", null, null, null, null);

        assertThat(result).hasSize(1);
    }
}
