package isi.dan.ms_productos.dao;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import isi.dan.ms_productos.modelo.Producto;

public interface ProductoRepository extends JpaRepository<Producto, Long>, JpaSpecificationExecutor<Producto> {

    Optional<Producto> findByIdAndHabilitadoTrue(Long id);

    Optional<Producto> findFirstByCodigo(String codigo);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Producto p set p.stockActual = p.stockActual - :cantidad, "
            + "p.actualizadoPor = :actor, p.fechaActualizacion = :fecha "
            + "where p.id = :id and p.habilitado = true and p.stockActual >= :cantidad")
    int descontarStock(@Param("id") Long id, @Param("cantidad") BigDecimal cantidad,
                       @Param("actor") String actor, @Param("fecha") Instant fecha);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Producto p set p.stockActual = p.stockActual + :cantidad, "
            + "p.actualizadoPor = :actor, p.fechaActualizacion = :fecha "
            + "where p.id = :id and p.habilitado = true")
    int reponerStock(@Param("id") Long id, @Param("cantidad") BigDecimal cantidad,
                     @Param("actor") String actor, @Param("fecha") Instant fecha);
}
