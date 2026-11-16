package isi.dan.msclientes.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import isi.dan.msclientes.model.Pago;

public interface PagoRepository extends JpaRepository<Pago, Integer> {

    List<Pago> findByClienteIdAndHabilitadoTrueOrderByFechaDesc(Integer clienteId);

    Optional<Pago> findByIdAndHabilitadoTrue(Integer id);
}
