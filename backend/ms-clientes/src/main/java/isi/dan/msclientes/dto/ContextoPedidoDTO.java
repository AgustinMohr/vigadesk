package isi.dan.msclientes.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Datos que ms-pedidos necesita para validar y completar un pedido: el cliente y, si se pidio,
 * la obra con su dueno y estado. Si la obra no existe o esta dada de baja, los campos de obra
 * quedan en null.
 */
@Data
@NoArgsConstructor
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
