package isi.dan.msclientes.servicios;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import isi.dan.msclientes.dao.UsuarioRepository;
import isi.dan.msclientes.model.UsuarioCliente;

@Service
public class UsuarioService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public List<UsuarioCliente> findAll() {
        return usuarioRepository.findByHabilitadoTrue();
    }

    public Optional<UsuarioCliente> findById(Integer id) {
        return usuarioRepository.findByIdAndHabilitadoTrue(id);
    }

    public UsuarioCliente save(UsuarioCliente usuario) {
        if (usuarioRepository.existsByEmailAndHabilitadoTrue(usuario.getEmail())) {
            throw new IllegalArgumentException("Ya existe un usuario habilitado con el email " + usuario.getEmail());
        }
        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
        return usuarioRepository.save(usuario);
    }

    public UsuarioCliente update(UsuarioCliente existente, UsuarioCliente nuevo) {
        if (nuevo.getNombre() != null) {
            existente.setNombre(nuevo.getNombre());
        }
        if (nuevo.getApellido() != null) {
            existente.setApellido(nuevo.getApellido());
        }
        if (nuevo.getDni() != null) {
            existente.setDni(nuevo.getDni());
        }
        if (nuevo.getEmail() != null) {
            usuarioRepository.findByEmailAndHabilitadoTrue(nuevo.getEmail())
                .filter(otro -> !otro.getId().equals(existente.getId()))
                .ifPresent(otro -> {
                    throw new IllegalArgumentException("Ya existe un usuario habilitado con el email " + nuevo.getEmail());
                });
            existente.setEmail(nuevo.getEmail());
        }
        if (nuevo.getPassword() != null && !nuevo.getPassword().isBlank()) {
            existente.setPassword(passwordEncoder.encode(nuevo.getPassword()));
        }
        if (nuevo.getCliente() != null && nuevo.getCliente().getId() != null) {
            existente.setCliente(nuevo.getCliente());
        }
        return usuarioRepository.save(existente);
    }

    public void deleteById(Integer id) {
        usuarioRepository.findByIdAndHabilitadoTrue(id).ifPresent(usuario -> {
            usuario.setHabilitado(false);
            usuario.setFechaBorrado(Instant.now());
            usuarioRepository.save(usuario);
        });
    }

    public Optional<UsuarioCliente> validarLogin(String email, String rawPassword) {
        return usuarioRepository.findByEmailAndHabilitadoTrue(email)
                .filter(usuario -> passwordEncoder.matches(rawPassword, usuario.getPassword()));
    }
}
