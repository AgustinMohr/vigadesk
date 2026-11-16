package isi.dan.ms.pedidos.modelo;

import java.time.Instant;

import lombok.Data;

@Data
public class AuditLog {

    private Instant fecha;
    private String actor;
    private String accion;
    private String detalle;
}
