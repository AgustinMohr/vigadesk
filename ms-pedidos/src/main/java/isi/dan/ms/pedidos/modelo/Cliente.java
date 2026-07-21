package isi.dan.ms.pedidos.modelo;


import org.springframework.data.annotation.Id;

import lombok.Data;

@Data
public class Cliente {
    
	@Id
    private Integer id;
    private String nombre;
    private String correoElectronico;
    private String cuit;

}
