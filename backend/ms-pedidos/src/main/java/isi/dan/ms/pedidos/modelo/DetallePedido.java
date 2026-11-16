package isi.dan.ms.pedidos.modelo;

import java.math.BigDecimal;

import org.springframework.data.mongodb.core.mapping.Field;

import lombok.Data;

@Data
public class DetallePedido {

    private String lineaId;

    @Field("producto")
    private Producto producto;

    private BigDecimal cantidad;

    private BigDecimal cantidadEntregada = BigDecimal.ZERO;

    private BigDecimal precioUnitario;

    private BigDecimal descuento;

    private BigDecimal precioFinal;

    public BigDecimal getCantidadEntregada() {
        return cantidadEntregada == null ? BigDecimal.ZERO : cantidadEntregada;
    }

    public BigDecimal getCantidadPendiente() {
        if (cantidad == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal pendiente = cantidad.subtract(getCantidadEntregada());
        return pendiente.signum() < 0 ? BigDecimal.ZERO : pendiente;
    }
}
