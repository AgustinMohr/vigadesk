package isi.dan.msclientes.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import isi.dan.msclientes.model.Cliente;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Integer> {

    List<Cliente> findByHabilitadoTrue();

    Optional<Cliente> findByIdAndHabilitadoTrue(Integer id);
}
