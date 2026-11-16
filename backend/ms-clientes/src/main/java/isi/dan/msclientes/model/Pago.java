package isi.dan.msclientes.model;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Entity
@Table(name = "MS_CLI_PAGO")
@Data
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "ID_CLIENTE", nullable = false)
    private Cliente cliente;

    @Column(name = "FECHA")
    private Instant fecha;

    @NotNull(message = "El monto es obligatorio")
    @Positive(message = "El monto debe ser mayor a cero")
    @Column(name = "MONTO", precision = 19, scale = 2)
    private BigDecimal monto;

    @NotBlank(message = "El metodo de pago es obligatorio")
    @Column(name = "METODO_PAGO")
    private String metodoPago;

    @Column(name = "OBSERVACION")
    private String observacion;

    @Column(name = "REGISTRADO_POR")
    private String registradoPor;

    @Column(name = "HABILITADO")
    private boolean habilitado = true;

    @Column(name = "FECHA_BORRADO")
    private Instant fechaBorrado;
}
