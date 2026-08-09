package isi.dan.ms.pedidos.dto;

import isi.dan.ms.pedidos.modelo.EstadoPedido;
import lombok.Data;

@Data
public class EstadoUpdateRequest {
    private EstadoPedido estado;
}
