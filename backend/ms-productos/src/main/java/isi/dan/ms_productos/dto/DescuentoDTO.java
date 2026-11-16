package isi.dan.ms_productos.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Payload para el endpoint PUT /api/productos/{id}/descuento.
 *
 * Semántica del campo: descuentoPromocional es un PORCENTAJE entre 0 y 100
 * (ej: 10 = 10%). El backend de pedidos lo aplica como
 * precio × (1 − descuento/100); un valor fuera de ese rango rompería el
 * cálculo, por eso se valida acá en la frontera HTTP (400).
 */
@Data
public class DescuentoDTO {

    @NotNull(message = "El descuento promocional es obligatorio")
    @DecimalMin(value = "0", message = "El descuento promocional debe ser mayor o igual a 0")
    @DecimalMax(value = "100", message = "El descuento promocional debe ser menor o igual a 100")
    private BigDecimal descuentoPromocional;
}
