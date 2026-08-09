package isi.dan.ms.pedidos.controller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import isi.dan.ms.pedidos.dto.EstadoUpdateRequest;
import isi.dan.ms.pedidos.modelo.EstadoPedido;
import isi.dan.ms.pedidos.modelo.Pedido;
import isi.dan.ms.pedidos.servicio.PedidoService;

import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {
    
    @Autowired
    private PedidoService pedidoService;

    @PostMapping
    public ResponseEntity<Pedido> createPedido(@RequestBody @Validated Pedido pedido) {
        Pedido savedPedido = pedidoService.savePedido(pedido);
        return ResponseEntity.ok(savedPedido);
    }

    @GetMapping
    public List<Pedido> getAllPedidos() {
        return pedidoService.getAllPedidos();
    }

    @GetMapping("/search")
    public List<Pedido> searchPedidos(
            @RequestParam(required = false) String clienteNombre,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Integer clienteId) {
        EstadoPedido estadoEnum = (estado != null && !estado.isBlank())
                ? EstadoPedido.valueOf(estado.toUpperCase())
                : null;
        return pedidoService.searchPedidos(clienteNombre, estadoEnum, clienteId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pedido> getPedidoById(@PathVariable String id) {
        return ResponseEntity.ok(pedidoService.getPedidoById(id));
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<Pedido> updateEstado(@PathVariable String id, @RequestBody EstadoUpdateRequest request) {
        return ResponseEntity.ok(pedidoService.updateEstado(id, request.getEstado()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePedido(@PathVariable String id) {
        pedidoService.getPedidoById(id);
        pedidoService.deletePedido(id);
        return ResponseEntity.noContent().build();
    }
}

