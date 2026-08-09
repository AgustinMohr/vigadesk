package isi.dan.msclientes.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import isi.dan.msclientes.model.UsuarioCliente;

@Repository
public interface UsuarioRepository extends JpaRepository<UsuarioCliente, Integer> {

    List<UsuarioCliente> findByHabilitadoTrue();

    Optional<UsuarioCliente> findByIdAndHabilitadoTrue(Integer id);

    Optional<UsuarioCliente> findByEmailAndHabilitadoTrue(String email);

    boolean existsByEmailAndHabilitadoTrue(String email);
}
