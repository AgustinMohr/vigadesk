package isi.dan.msclientes.exception;

import java.time.Instant;

// DTO inmutable para respuestas de error uniformes
public record ErrorInfo(Instant fecha, String description, String detalle, Integer codigo) {
}    
