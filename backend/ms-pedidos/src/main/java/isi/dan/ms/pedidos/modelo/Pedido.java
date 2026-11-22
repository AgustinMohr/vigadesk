package isi.dan.ms.pedidos.modelo;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// La clave de idempotencia es unica por cliente, no global. El indice es parcial (solo pedidos con clave):
// uno sparse compuesto igual indexaria los pedidos sin clave y los repetiria por cliente.
@CompoundIndex(name = "cliente_idempotencyKey", def = "{'cliente._id': 1, 'idempotencyKey': 1}", unique = true,
        partialFilter = "{'idempotencyKey': {'$type': 'string'}}")
// Indices de las busquedas: por cliente y estado (listado del cliente, consumo de credito) y por fecha (orden).
@CompoundIndex(name = "cliente_estado", def = "{'cliente._id': 1, 'estado': 1}")
@CompoundIndex(name = "fecha_desc", def = "{'fecha': -1}")
@Document(collection = "pedidos")
@Data
@NoArgsConstructor
public class Pedido {

    @Id
    private String id;
    private Instant fecha;
    
    @NotNull(message = "El número de pedido es obligatorio")
    private Integer numeroPedido;

    private String usuario;
    private String observaciones;

    private String idempotencyKey;

    /** Huella del payload que creo el pedido: la misma clave con otro payload es un conflicto. */
    private String idempotencyHash;

    @NotNull(message = "El cliente es obligatorio")
    private Cliente cliente;
    
    private Obra obra;

    @NotNull(message = "El total es obligatorio")
    @Positive(message = "El total debe ser mayor a cero")
    private BigDecimal total;

    @NotNull(message = "El estado es obligatorio")
    private EstadoPedido estado;

    @Field("detalle")
    @NotNull(message = "El detalle del pedido no puede ser nulo")
    @NotEmpty(message = "El pedido debe contener al menos un ítem en el detalle")
    private List<DetallePedido> detalle;

    @Field("entregas")
    private List<Entrega> entregas = new ArrayList<>();

    @Field("auditoria")
    private List<AuditLog> auditoria = new ArrayList<>();

    private boolean habilitado = true;

    private Instant fechaBorrado;
}

