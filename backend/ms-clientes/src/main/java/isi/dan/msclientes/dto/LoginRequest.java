package isi.dan.msclientes.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @Email(message = "Email debe ser valido")
    @NotBlank(message = "Email es obligatorio")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    private String password;
}
