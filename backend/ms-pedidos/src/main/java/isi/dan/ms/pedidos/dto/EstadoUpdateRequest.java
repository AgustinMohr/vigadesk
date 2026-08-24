package isi.dan.ms.pedidos.dto;

import isi.dan.ms.pedidos.modelo.EstadoPedido;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EstadoUpdateRequest {

    @NotNull
    private EstadoPedido estado;
}
