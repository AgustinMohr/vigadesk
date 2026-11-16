package isi.dan.ms.pedidos.dto;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class EntregaRequest {

    @NotBlank(message = "El receptor es obligatorio")
    private String receptor;

    private String observaciones;

    @Valid
    @NotEmpty(message = "La entrega debe contener al menos un item")
    private List<EntregaItemRequest> items = new ArrayList<>();
}
