package isi.dan.ms.pedidos.dto;

import lombok.Data;

/** Respuesta de ms-clientes (GET /api/clientes/{id}/contexto-pedido). */
@Data
public class ContextoPedidoDTO {
    private Integer clienteId;
    private String clienteNombre;
    private String clienteCorreoElectronico;
    private String clienteCuit;

    private Integer obraId;
    private String obraDireccion;
    private String obraEstado;
    private Integer obraClienteId;
}
