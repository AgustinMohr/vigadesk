package isi.dan.ms_productos.modelo;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

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
    @Column(name ="STOCK_ACTUAL")
	@NotNull
    private int stockActual;
    @Column(name ="STOCK_MINIMO")
	@NotNull
    private int stockMinimo;
	@NotNull
    private BigDecimal precio;

    @Column(name ="DESCUENTO_PROMOCIONAL")
    private BigDecimal descuentoPromocional = BigDecimal.ZERO;
    
    @Enumerated(EnumType.STRING)
    private Categoria categoria;

}
