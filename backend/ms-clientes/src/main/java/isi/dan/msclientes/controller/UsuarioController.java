package isi.dan.msclientes.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import isi.dan.msclientes.aop.LogExecutionTime;
import isi.dan.msclientes.dto.LoginRequest;
import isi.dan.msclientes.dto.LoginResponse;
import isi.dan.msclientes.exception.UsuarioNotFoundException;
import isi.dan.msclientes.model.UsuarioCliente;
import isi.dan.msclientes.security.JwtService;
import isi.dan.msclientes.servicios.UsuarioService;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private JwtService jwtService;

    @GetMapping
    @LogExecutionTime
    public List<UsuarioCliente> getAll() {
        return usuarioService.findAll();
    }

    @GetMapping("/{id}")
    @LogExecutionTime
    public ResponseEntity<UsuarioCliente> getById(@PathVariable Integer id) throws UsuarioNotFoundException {
        return ResponseEntity.ok(usuarioService.findById(id)
                .orElseThrow(() -> new UsuarioNotFoundException("Usuario " + id + " no encontrado")));
    }

    @PostMapping
    @LogExecutionTime
    public ResponseEntity<?> create(@RequestBody @Validated UsuarioCliente usuario) {
        return ResponseEntity.ok(usuarioService.save(usuario));
    }

    @PutMapping("/{id}")
    @LogExecutionTime
    public ResponseEntity<?> update(@PathVariable Integer id, @RequestBody UsuarioCliente usuario)
            throws UsuarioNotFoundException {
        UsuarioCliente existente = usuarioService.findById(id)
                .orElseThrow(() -> new UsuarioNotFoundException("Usuario " + id + " no encontrado"));
        return ResponseEntity.ok(usuarioService.update(existente, usuario));
    }

    @DeleteMapping("/{id}")
    @LogExecutionTime
    public ResponseEntity<Void> delete(@PathVariable Integer id) throws UsuarioNotFoundException {
        if (usuarioService.findById(id).isEmpty()) {
            throw new UsuarioNotFoundException("Usuario " + id + " no encontrado para borrar");
        }
        usuarioService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    @LogExecutionTime
    public ResponseEntity<LoginResponse> login(@RequestBody @Validated LoginRequest request) {
        return usuarioService.validarLogin(request.getEmail(), request.getPassword())
                .map(u -> ResponseEntity.ok(toLoginResponse(u)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    private LoginResponse toLoginResponse(UsuarioCliente u) {
        LoginResponse resp = new LoginResponse();
        resp.setToken(jwtService.generateToken(u));
        resp.setEmail(u.getEmail());
        resp.setNombre(u.getNombre());
        resp.setRol(u.getRol() != null ? u.getRol() : "CLIENTE");
        if (u.getCliente() != null) {
            resp.setClienteId(u.getCliente().getId());
            resp.setClienteNombre(u.getCliente().getNombre());
        }
        return resp;
    }
}
