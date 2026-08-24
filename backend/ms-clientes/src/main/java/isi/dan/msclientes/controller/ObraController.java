package isi.dan.msclientes.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import isi.dan.msclientes.aop.LogExecutionTime;
import isi.dan.msclientes.exception.ObraNotFoundException;
import isi.dan.msclientes.model.EstadoObra;
import isi.dan.msclientes.model.Obra;
import isi.dan.msclientes.servicios.ObraService;

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
        Obra obra = obraService.findById(id)
                .orElseThrow(() -> new ObraNotFoundException("Obra " + id + " no encontrada"));
        return ResponseEntity.ok(obra);
    }

    @PostMapping
    public ResponseEntity<Obra> create(@RequestBody @Validated Obra obra) {
        return ResponseEntity.ok(obraService.save(obra));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Obra> update(@PathVariable Integer id, @RequestBody @Validated Obra obra) {
        Obra existente = obraService.findById(id)
                .orElseThrow(() -> new ObraNotFoundException("Obra " + id + " no encontrada"));
        obra.setId(existente.getId());
        return ResponseEntity.ok(obraService.update(obra));
    }

    @PutMapping("/{obraId}/asignar-cliente/{clienteId}")
    public ResponseEntity<Obra> asignarCliente(@PathVariable Integer obraId, @PathVariable Integer clienteId) {
        return ResponseEntity.ok(obraService.asignarCliente(obraId, clienteId));
    }

    @PutMapping("/{id}/estado")
    public ResponseEntity<Obra> cambiarEstado(@PathVariable Integer id, @RequestBody Map<String, String> body) {
        String estado = body.get("estado");
        if (estado == null || estado.isBlank()) {
            throw new IllegalArgumentException("El campo 'estado' es obligatorio");
        }
        return ResponseEntity.ok(obraService.cambiarEstado(id, EstadoObra.valueOf(estado.toUpperCase())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        Obra obra = obraService.findById(id)
                .orElseThrow(() -> new ObraNotFoundException("Obra " + id + " no encontrada"));
        obraService.deleteById(obra.getId());
        return ResponseEntity.noContent().build();
    }
}
