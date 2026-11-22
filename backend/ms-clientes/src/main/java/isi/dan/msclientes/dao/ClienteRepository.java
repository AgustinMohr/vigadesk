package isi.dan.msclientes.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import isi.dan.msclientes.model.Cliente;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Integer> {

    List<Cliente> findByHabilitadoTrue();

    Page<Cliente> findByHabilitadoTrue(Pageable pageable);

    Optional<Cliente> findByIdAndHabilitadoTrue(Integer id);

    Optional<Cliente> findByCorreoElectronico(String correoElectronico);
}
