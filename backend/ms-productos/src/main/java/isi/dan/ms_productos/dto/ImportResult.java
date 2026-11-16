package isi.dan.ms_productos.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ImportResult {
    private int creados;
    private int actualizados;
    private String mensaje;
}
