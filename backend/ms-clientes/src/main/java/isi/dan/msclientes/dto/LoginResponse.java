package isi.dan.msclientes.dto;

import lombok.Data;

// Respuesta del endpoint /api/usuarios/login.
// Devuelve el token firmado + los datos del usuario que el frontend
// necesita para armar la sesion (rol y clienteId, si aplica).
@Data
public class LoginResponse {

    private String token;
    private String email;
    private String nombre;
    private String rol;
    private Integer clienteId;
    private String clienteNombre;
}
