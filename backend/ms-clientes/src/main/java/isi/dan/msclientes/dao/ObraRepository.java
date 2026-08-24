package isi.dan.msclientes.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import isi.dan.msclientes.model.EstadoObra;
import isi.dan.msclientes.model.Obra;

@Repository
public interface ObraRepository extends JpaRepository<Obra, Integer> {

    List<Obra> findByHabilitadoTrue();

    Optional<Obra> findByIdAndHabilitadoTrue(Integer id);

    List<Obra> findByClienteIdAndHabilitadoTrue(Integer clienteId);

    List<Obra> findByClienteIsNullAndHabilitadoTrue();

    long countByClienteIdAndEstadoAndHabilitadoTrue(Integer clienteId, EstadoObra estado);

    List<Obra> findByClienteIdAndEstadoAndHabilitadoTrueOrderByIdAsc(Integer clienteId, EstadoObra estado);
}
