package isi.dan.msclientes.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import isi.dan.msclientes.aop.LogExecutionTime;
import isi.dan.msclientes.model.EstadoObra;
import isi.dan.msclientes.model.Obra;
import isi.dan.msclientes.servicios.ObraService;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/obras")
public class ObraController {

    @Autowired
    private ObraService obraService;

    @GetMapping
    @LogExecutionTime
    public List<Obra> getAll() {
        return obraService.findAll();
    }

    @GetMapping("/sin-asignar")
    @LogExecutionTime
    public List<Obra> getSinAsignar() {
        return obraService.findByClienteIsNull();
    }

    @GetMapping("/by-cliente/{clienteId}")
    @LogExecutionTime
    public List<Obra> getByClienteId(@PathVariable Integer clienteId) {
        return obraService.findByClienteId(clienteId);
    }

    @GetMapping("/{id}")
    @LogExecutionTime
    public ResponseEntity<Obra> getById(@PathVariable Integer id) {
        Optional<Obra> obra = obraService.findById(id);
        return obra.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Obra obra) {
        try {
            Obra saved = obraService.save(obra);
            return ResponseEntity.ok(saved);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Obra> update(@PathVariable Integer id, @RequestBody Obra obra) {
        if (!obraService.findById(id).isPresent()) {
            return ResponseEntity.notFound().build();
        }
        obra.setId(id);
        return ResponseEntity.ok(obraService.update(obra));
    }

    @PutMapping("/{obraId}/asignar-cliente/{clienteId}")
    public ResponseEntity<?> asignarCliente(@PathVariable Integer obraId, @PathVariable Integer clienteId) {
        try {
            Obra obra = obraService.asignarCliente(obraId, clienteId);
            return ResponseEntity.ok(obra);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/estado")
    public ResponseEntity<?> cambiarEstado(@PathVariable Integer id, @RequestBody Map<String, String> body) {
        try {
            EstadoObra nuevoEstado = EstadoObra.valueOf(body.get("estado"));
            Obra obra = obraService.cambiarEstado(id, nuevoEstado);
            return ResponseEntity.ok(obra);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Estado no valido: " + body.get("estado")));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        if (!obraService.findById(id).isPresent()) {
            return ResponseEntity.notFound().build();
        }
        obraService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
