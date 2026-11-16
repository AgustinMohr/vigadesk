package isi.dan.ms_productos.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import isi.dan.ms_productos.dao.ProductoRepository;
import isi.dan.ms_productos.dto.ImportResult;
import isi.dan.ms_productos.modelo.Categoria;
import isi.dan.ms_productos.modelo.Producto;
import isi.dan.ms_productos.modelo.UnidadMedida;

@ExtendWith(MockitoExtension.class)
class ProductoImportServiceTest {

    @Mock
    private ProductoRepository productoRepository;

    @InjectMocks
    private ProductoImportService service;

    private ByteArrayInputStream csv(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void importar_ShouldCreateProductWithDecimalStockAndUnit() {
        when(productoRepository.findFirstByCodigo("C1")).thenReturn(Optional.empty());
        when(productoRepository.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));

        ImportResult result = service.importar(csv(
                "codigo,nombre,unidadMedida,precio,stockActual,stockMinimo,costo,categoria\n"
                        + "C1,Cemento,BOLSA,1000,10.5,2,800,CEMENTOS\n"), "Admin");

        ArgumentCaptor<Producto> captor = ArgumentCaptor.forClass(Producto.class);
        verify(productoRepository).save(captor.capture());
        Producto producto = captor.getValue();
        assertThat(result.getCreados()).isEqualTo(1);
        assertThat(producto.getStockActual()).isEqualByComparingTo(new BigDecimal("10.5"));
        assertThat(producto.getUnidadMedida()).isEqualTo(UnidadMedida.BOLSA);
        assertThat(producto.getCategoria()).isEqualTo(Categoria.CEMENTOS);
        assertThat(producto.getCosto()).isEqualByComparingTo(BigDecimal.valueOf(800));
        assertThat(producto.getActualizadoPor()).isEqualTo("Admin");
    }

    @Test
    void importar_WhenCodigoExists_ShouldUpdateInsteadOfDuplicate() {
        Producto existente = new Producto();
        existente.setId(9L);
        existente.setCodigo("C1");
        when(productoRepository.findFirstByCodigo("C1")).thenReturn(Optional.of(existente));
        when(productoRepository.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));

        ImportResult result = service.importar(csv(
                "codigo,nombre,unidadMedida,precio,stockActual,stockMinimo\n"
                        + "C1,Cemento Actualizado,UNIDAD,1200,3,1\n"), "Admin");

        assertThat(result.getActualizados()).isEqualTo(1);
        assertThat(result.getCreados()).isZero();
        assertThat(existente.getPrecio()).isEqualByComparingTo(BigDecimal.valueOf(1200));
    }

    @Test
    void importar_WhenPrecioFaltante_ShouldThrow() {
        assertThatThrownBy(() -> service.importar(csv(
                "codigo,nombre,unidadMedida,precio,stockActual,stockMinimo\n"
                        + "C1,Cemento,BOLSA,,1,1\n"), "Admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("precio");
    }
}
