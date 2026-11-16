package isi.dan.ms.pedidos.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EntregaItemRequest {

    @NotBlank(message = "La linea del pedido es obligatoria")
    private String lineaId;

    @NotNull(message = "La cantidad entregada es obligatoria")
    @DecimalMin(value = "0.001", message = "La cantidad entregada debe ser mayor a cero")
    private BigDecimal cantidad;
}
