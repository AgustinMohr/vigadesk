package isi.dan.msclientes.model;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Entity
@Table(name = "MS_CLI_USUARIO")
@Data
public class UsuarioCliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @Column(name = "EMAIL")
    @Email(message = "Email debe ser valido")
    @NotBlank(message = "Email es obligatorio")
    private String email;

    // Se deserializa (write) pero nunca se expone en las respuestas (read)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @NotBlank(message = "La contraseña es obligatoria")
    private String password;

    @ManyToOne
    @JoinColumn(name = "ID_CLIENTE")
    private Cliente cliente;

    @Column(name = "ROL")
    private String rol = "CLIENTE";

    @Column(name = "HABILITADO")
    private boolean habilitado = true;

    @Column(name = "FECHA_BORRADO")
    private Instant fechaBorrado;
}
