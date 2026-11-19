package isi.dan.ms.pedidos.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * Cuerpo de alta de un pedido. Solo trae lo que decide el cliente; el resto (numero, fecha,
 * estado, totales, precios, datos del cliente, entregas, auditoria) lo fija el servidor.
 */
@Data
public class CrearPedidoRequest {

    /** Solo lo usa un ADMIN. Para un CLIENTE se toma del token y se ignora este valor. */
    private Integer clienteId;

    @NotNull(message = "La obra es obligatoria")
    private Integer obraId;

    private String observaciones;

    @NotEmpty(message = "El pedido debe contener al menos una linea")
    @Valid
    private List<Linea> lineas;

    @Data
    public static class Linea {

        @NotNull(message = "El producto es obligatorio")
        private Long productoId;

        @NotNull(message = "La cantidad es obligatoria")
        @Positive(message = "La cantidad debe ser mayor a cero")
        private BigDecimal cantidad;
    }
}
