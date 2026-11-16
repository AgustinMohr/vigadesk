package isi.dan.ms_productos.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Payload para el endpoint PUT /api/productos/provision.
 * La provision SUMA {@code cantidad} al stock del producto
 * {@code idProducto} y, opcionalmente, actualiza su precio.
 */
@Data
public class ProvisionDTO {

    @NotNull(message = "El id del producto es obligatorio")
    private Long idProducto;

    @NotNull(message = "La cantidad a reponer es obligatoria")
    @DecimalMin(value = "0.001", message = "La cantidad a reponer debe ser mayor a cero")
    private BigDecimal cantidad;

    // Opcional: si viene, actualiza el precio de venta del producto.
    private BigDecimal precio;
}
