package isi.dan.msclientes.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import isi.dan.msclientes.aop.LogExecutionTime;
import isi.dan.msclientes.exception.ClienteNotFoundException;
import isi.dan.msclientes.exception.ForbiddenException;
import isi.dan.msclientes.model.Pago;
import isi.dan.msclientes.servicios.PagoService;

@RestController
@RequestMapping("/api/clientes/{clienteId}/pagos")
public class PagoController {

    @Autowired
    private PagoService pagoService;

    @GetMapping
    @LogExecutionTime
    public List<Pago> getAll(
            @PathVariable Integer clienteId,
            @RequestHeader(value = "X-User-Rol", required = false) String rol,
            @RequestHeader(value = "X-User-ClienteId", required = false) Integer tokenClienteId) {
        ensureOwnership(clienteId, rol, tokenClienteId);
        return pagoService.findByCliente(clienteId);
    }

    @PostMapping
    @LogExecutionTime
    public ResponseEntity<Pago> create(
            @PathVariable Integer clienteId,
            @RequestBody @Validated Pago pago,
            @RequestHeader(value = "X-User-Rol", required = false) String rol,
            @RequestHeader(value = "X-User-Nombre", required = false) String userNombre,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail) throws ClienteNotFoundException {
        ensureAdmin(rol);
        return ResponseEntity.ok(pagoService.registrar(clienteId, pago, actor(userNombre, userEmail)));
    }

    @DeleteMapping("/{pagoId}")
    @LogExecutionTime
    public ResponseEntity<Void> delete(
            @PathVariable Integer clienteId,
            @PathVariable Integer pagoId,
            @RequestHeader(value = "X-User-Rol", required = false) String rol) {
        ensureAdmin(rol);
        pagoService.delete(clienteId, pagoId);
        return ResponseEntity.noContent().build();
    }

    private void ensureOwnership(Integer clienteId, String rol, Integer tokenClienteId) {
        if (isCliente(rol) && (tokenClienteId == null || !clienteId.equals(tokenClienteId))) {
            throw new ForbiddenException("No podes acceder a pagos de otro cliente");
        }
    }

    private void ensureAdmin(String rol) {
        if (!"ADMIN".equalsIgnoreCase(rol)) {
            throw new ForbiddenException("Solo un administrador puede registrar o borrar pagos");
        }
    }

    private boolean isCliente(String rol) {
        return "CLIENTE".equalsIgnoreCase(rol);
    }

    private String actor(String userNombre, String userEmail) {
        if (userNombre != null && !userNombre.isBlank()) {
            return userNombre.trim();
        }
        if (userEmail != null && !userEmail.isBlank()) {
            return userEmail.trim();
        }
        return "sistema";
    }
}
