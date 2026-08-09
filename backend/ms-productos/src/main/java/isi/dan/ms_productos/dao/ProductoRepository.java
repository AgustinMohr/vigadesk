package isi.dan.ms_productos.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import isi.dan.ms_productos.modelo.Producto;

public interface ProductoRepository extends JpaRepository<Producto, Long>, JpaSpecificationExecutor<Producto> {

    List<Producto> findByHabilitadoTrue();

    Optional<Producto> findByIdAndHabilitadoTrue(Long id);
}
