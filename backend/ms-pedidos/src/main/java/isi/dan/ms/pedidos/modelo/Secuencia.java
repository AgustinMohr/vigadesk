package isi.dan.ms.pedidos.modelo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document(collection = "secuencias")
public class Secuencia {

    @Id
    private String id;

    private long valor;
}
