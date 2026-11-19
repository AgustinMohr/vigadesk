package isi.dan.ms.pedidos.modelo;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

/**
 * Bloqueo por cliente para serializar el control de credito. Expira solo,
 * para que un proceso caido no deje al cliente bloqueado.
 */
@Data
@Document(collection = "bloqueos_cliente")
public class BloqueoCliente {

    @Id
    private String id;

    private String token;

    private Instant expiraEn;
}
