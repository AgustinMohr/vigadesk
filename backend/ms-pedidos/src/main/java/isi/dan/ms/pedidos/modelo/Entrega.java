package isi.dan.ms.pedidos.modelo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class Entrega {

    private String id;
    private Integer numeroRemito;
    private Instant fecha;
    private String receptor;
    private String observaciones;
    private List<EntregaItem> items = new ArrayList<>();
}
