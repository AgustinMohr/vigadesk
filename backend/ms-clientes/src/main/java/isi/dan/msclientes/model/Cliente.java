package isi.dan.msclientes.model;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Entity
@Table(name = "MS_CLI_CLIENTE")
@Data
public class Cliente {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @Column(name="CORREO_ELECTRONICO")
    @Email(message = "Email debe ser valido")
    @NotBlank(message = "Email es obligatorio")
    private String correoElectronico;
    
    private String cuit;

    @Column(name="MAXIMO_DESCUBIERTO")
    @Positive(message = "El descubierto maximo debe ser un valor mayor que cero")
    private BigDecimal maximoDescubierto;

    @NotNull(message = "El maximo de obras activas es obligatorio")
    @Column(name="MAX_OBRAS_ACTIVAS")
    private Integer maxObrasActivas;

    @Column(name = "HABILITADO")
    private boolean habilitado = true;

    @Column(name = "FECHA_BORRADO")
    private Instant fechaBorrado;
    
}
