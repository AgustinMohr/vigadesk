package isi.dan.ms.pedidos.modelo;

import org.springframework.data.annotation.Id;

import lombok.Data;

@Data
public class Obra {
	@Id
    private Integer id;
    private String direccion;
}
