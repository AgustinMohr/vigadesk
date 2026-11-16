package isi.dan.ms.pedidos.modelo;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

    @Indexed(unique = true, sparse = true)
    private String idempotencyKey;

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

