package isi.dan.ms_productos.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;

import isi.dan.ms_productos.dao.ProductoRepository;
import isi.dan.ms_productos.dto.DescuentoDTO;
import isi.dan.ms_productos.dto.ProvisionDTO;
import isi.dan.ms_productos.dto.StockUpdateDTO;
import isi.dan.ms_productos.exception.ProductoNotFoundException;
import isi.dan.ms_productos.modelo.Categoria;
import isi.dan.ms_productos.modelo.Producto;

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
        producto.setStockActual(100);
        producto.setStockMinimo(10);
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
    void checkStock_WhenAllHaveStock_ShouldReturnTrue() {
        StockUpdateDTO item = new StockUpdateDTO();
        item.setIdProducto(1L);
        item.setCantidad(50);
        when(productoRepository.findByIdAndHabilitadoTrue(1L)).thenReturn(Optional.of(producto));

        boolean result = productoService.checkStock(Collections.singletonList(item));

        assertThat(result).isTrue();
    }

    @Test
    void checkStock_WhenStockInsuficiente_ShouldReturnFalse() {
        StockUpdateDTO item = new StockUpdateDTO();
        item.setIdProducto(1L);
        item.setCantidad(200);
        when(productoRepository.findByIdAndHabilitadoTrue(1L)).thenReturn(Optional.of(producto));

        boolean result = productoService.checkStock(Collections.singletonList(item));

        assertThat(result).isFalse();
    }

    @Test
    void checkStock_WhenProductoNoExiste_ShouldThrowRuntimeException() {
        StockUpdateDTO item = new StockUpdateDTO();
        item.setIdProducto(99L);
        item.setCantidad(10);
        when(productoRepository.findByIdAndHabilitadoTrue(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productoService.checkStock(Collections.singletonList(item)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Producto no encontrado: 99");
    }

    @Test
    void checkStock_MultipleItems_AllHaveStock_ShouldReturnTrue() {
        StockUpdateDTO item1 = new StockUpdateDTO();
        item1.setIdProducto(1L);
        item1.setCantidad(30);

        Producto producto2 = new Producto();
        producto2.setId(2L);
        producto2.setStockActual(50);
        producto2.setPrecio(BigDecimal.valueOf(3000));

        StockUpdateDTO item2 = new StockUpdateDTO();
        item2.setIdProducto(2L);
        item2.setCantidad(20);

        when(productoRepository.findByIdAndHabilitadoTrue(1L)).thenReturn(Optional.of(producto));
        when(productoRepository.findByIdAndHabilitadoTrue(2L)).thenReturn(Optional.of(producto2));

        boolean result = productoService.checkStock(List.of(item1, item2));

        assertThat(result).isTrue();
    }

    @Test
    void procesarProvision_ShouldUpdateStockAndPrice() {
        ProvisionDTO provision = new ProvisionDTO();
        provision.setIdProducto(1L);
        provision.setCantidad(50);
        provision.setPrecio(BigDecimal.valueOf(5500));

        when(productoRepository.findByIdAndHabilitadoTrue(1L)).thenReturn(Optional.of(producto));
        when(productoRepository.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));

        Producto result = productoService.procesarProvision(provision);

        assertThat(result.getStockActual()).isEqualTo(150);
        assertThat(result.getPrecio()).isEqualByComparingTo(BigDecimal.valueOf(5500));
    }

    @Test
    void procesarProvision_WhenProductoNoExiste_ShouldThrowRuntimeException() {
        ProvisionDTO provision = new ProvisionDTO();
        provision.setIdProducto(99L);
        when(productoRepository.findByIdAndHabilitadoTrue(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productoService.procesarProvision(provision))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Producto 99 no encontrado");
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
    void handleStockUpdate_ValidMessage_ShouldDecreaseStock() {
        Message msg = new Message("1;30".getBytes(StandardCharsets.UTF_8));
        when(productoRepository.findByIdAndHabilitadoTrue(1L)).thenReturn(Optional.of(producto));
        when(productoRepository.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));

        productoService.handleStockUpdate(msg);

        assertThat(producto.getStockActual()).isEqualTo(70);
        verify(productoRepository).save(producto);
    }

    @Test
    void handleStockUpdate_InvalidMessage_ShouldNotFail() {
        Message msg = new Message("formato-incorrecto".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> productoService.handleStockUpdate(msg))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    void handleStockUpdate_ProductoNoExiste_ShouldNotFail() {
        Message msg = new Message("99;10".getBytes(StandardCharsets.UTF_8));
        when(productoRepository.findByIdAndHabilitadoTrue(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productoService.handleStockUpdate(msg))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    void handleStockReturn_ValidMessage_ShouldIncreaseStock() {
        Message msg = new Message("1;20".getBytes(StandardCharsets.UTF_8));
        when(productoRepository.findByIdAndHabilitadoTrue(1L)).thenReturn(Optional.of(producto));
        when(productoRepository.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));

        productoService.handleStockReturn(msg);

        assertThat(producto.getStockActual()).isEqualTo(120);
        verify(productoRepository).save(producto);
    }

    @Test
    void handleStockReturn_InvalidMessage_ShouldNotFail() {
        Message msg = new Message("".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> productoService.handleStockReturn(msg))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    void handleStockReturn_ProductoNoExiste_ShouldNotFail() {
        Message msg = new Message("99;5".getBytes(StandardCharsets.UTF_8));
        when(productoRepository.findByIdAndHabilitadoTrue(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productoService.handleStockReturn(msg))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }
}
