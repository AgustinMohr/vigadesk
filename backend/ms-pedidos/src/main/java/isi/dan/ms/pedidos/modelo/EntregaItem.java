package isi.dan.ms.pedidos.modelo;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class EntregaItem {

    private String lineaId;
    private Long idProducto;
    private String productoNombre;
    private BigDecimal cantidad;
}
