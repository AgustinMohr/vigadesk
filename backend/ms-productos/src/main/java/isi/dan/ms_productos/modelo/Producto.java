package isi.dan.ms_productos.modelo;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ms_prd_producto")
@Data
public class Producto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String codigo;
    @NotNull
    private String nombre;
    private String descripcion;
    @Column(name ="STOCK_ACTUAL", precision = 19, scale = 3)
	@NotNull
    private BigDecimal stockActual = BigDecimal.ZERO;

    @Column(name ="STOCK_MINIMO", precision = 19, scale = 3)
	@NotNull
    private BigDecimal stockMinimo = BigDecimal.ZERO;

    @NotNull
    private BigDecimal precio;

    @Column(name ="COSTO", precision = 19, scale = 2)
    private BigDecimal costo;

    @Enumerated(EnumType.STRING)
    @Column(name = "UNIDAD_MEDIDA")
    private UnidadMedida unidadMedida = UnidadMedida.UNIDAD;

    // PORCENTAJE (0-100): lo aplica ms-pedidos como precio × (1 − descuento/100).
    // Fuera de rango → totales de pedido absurdos. Se valida en la frontera HTTP
    // (DescuentoDTO) y en ProductoService (validarDescuento). No se anota acá con
    // @DecimalMin/@DecimalMax para no romper el guardado de datos legacy con
    // valores fuera de rango aún sin remediar.
    @Column(name ="DESCUENTO_PROMOCIONAL")
    private BigDecimal descuentoPromocional = BigDecimal.ZERO;
    
    @Enumerated(EnumType.STRING)
    private Categoria categoria;

    private boolean habilitado = true;

    @Column(name = "FECHA_BORRADO")
    private Instant fechaBorrado;

    @Column(name = "ACTUALIZADO_POR")
    private String actualizadoPor;

    @Column(name = "FECHA_ACTUALIZACION")
    private Instant fechaActualizacion;

    @Version
    @Column(name = "VERSION")
    private Long version;

}
