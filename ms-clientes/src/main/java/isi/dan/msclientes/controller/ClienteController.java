package isi.dan.msclientes.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import isi.dan.msclientes.aop.LogExecutionTime;
import isi.dan.msclientes.exception.ClienteNotFoundException;
import isi.dan.msclientes.model.Cliente;
import isi.dan.msclientes.servicios.ClienteService;


import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    Logger log = LoggerFactory.getLogger(ClienteController.class);

    @Autowired
    private ClienteService clienteService;

    @GetMapping
    @LogExecutionTime
    public List<Cliente> getAll() {
        return clienteService.findAll();
    }
    

    @GetMapping("/{id}")
    @LogExecutionTime
    public ResponseEntity<Cliente> getById(@PathVariable Integer id)  throws ClienteNotFoundException {
        Optional<Cliente> cliente = clienteService.findById(id);
        return ResponseEntity.ok(cliente.orElseThrow(()-> new ClienteNotFoundException("Cliente "+id+" no encontrado")));
    }

    @PostMapping
    @LogExecutionTime
    public Cliente create(@RequestBody @Validated Cliente cliente) {
        return clienteService.save(cliente);
    }

    @PutMapping("/{id}")
public ResponseEntity<Cliente> update(@PathVariable Integer id, @RequestBody Cliente cliente) throws ClienteNotFoundException {
    Cliente existente = clienteService.findById(id)
        .orElseThrow(() -> new ClienteNotFoundException("Cliente " + id + " no encontrado"));

    return ResponseEntity.ok(clienteService.update(existente, cliente));
}

    @DeleteMapping("/{id}")
    @LogExecutionTime
    public ResponseEntity<Void> delete(@PathVariable Integer id) throws ClienteNotFoundException {
        if (!clienteService.findById(id).isPresent()) {
            throw new ClienteNotFoundException("Cliente "+id+" no encontrado para borrar");
        }
        clienteService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/saldo")
    @LogExecutionTime
    public ResponseEntity<Boolean> checkSaldo(@PathVariable Integer id, @RequestParam BigDecimal monto) throws ClienteNotFoundException {
        if (!clienteService.findById(id).isPresent()) {
            throw new ClienteNotFoundException("Cliente "+id+" no encontrado");
        }
        return ResponseEntity.ok(clienteService.checkSaldo(id, monto));
    }

    @GetMapping("/{id}/maximo-descubierto")
    @LogExecutionTime
    public ResponseEntity<BigDecimal> getMaximoDescubierto(@PathVariable Integer id) throws ClienteNotFoundException {
        if (!clienteService.findById(id).isPresent()) {
            throw new ClienteNotFoundException("Cliente "+id+" no encontrado");
        }
        return ResponseEntity.ok(clienteService.getMaximoDescubierto(id));
    }
}

