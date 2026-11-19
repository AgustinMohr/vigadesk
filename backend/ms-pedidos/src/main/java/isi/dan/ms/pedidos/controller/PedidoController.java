package isi.dan.ms.pedidos.controller;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import isi.dan.ms.pedidos.dto.EntregaRequest;
import isi.dan.ms.pedidos.dto.EstadoUpdateRequest;
import isi.dan.ms.pedidos.exception.ForbiddenException;
import isi.dan.ms.pedidos.modelo.Cliente;
import isi.dan.ms.pedidos.modelo.Entrega;
import isi.dan.ms.pedidos.modelo.EstadoPedido;
import isi.dan.ms.pedidos.modelo.Pedido;
import isi.dan.ms.pedidos.servicio.PedidoPdfService;
import isi.dan.ms.pedidos.servicio.PedidoService;

import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {
    
    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private PedidoPdfService pedidoPdfService;

    @PostMapping
    public ResponseEntity<Pedido> createPedido(
            @RequestBody @Validated Pedido pedido,
            @RequestHeader(value = "X-User-Rol", required = false) String rol,
            @RequestHeader(value = "X-User-ClienteId", required = false) Integer tokenClienteId,
            @RequestHeader(value = "X-User-Nombre", required = false) String userNombre,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        if (isCliente(rol)) {
            if (tokenClienteId == null) {
                throw new ForbiddenException("La sesion no tiene cliente asociado");
            }
            Cliente cliente = pedido.getCliente();
            if (cliente == null) {
                cliente = new Cliente();
                pedido.setCliente(cliente);
            }
            cliente.setId(tokenClienteId);
        } else if (!isAdmin(rol)) {
            throw new ForbiddenException("No tenes permisos para crear pedidos");
        }
        Pedido savedPedido = pedidoService.savePedido(
                pedido, rol, idempotencyKey, actor(userNombre, userEmail));
        return ResponseEntity.ok(savedPedido);
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

    private void ensureOwnership(Pedido pedido, String rol, Integer tokenClienteId) {
        if (isCliente(rol) && (pedido.getCliente() == null || tokenClienteId == null
                || !pedido.getCliente().getId().equals(tokenClienteId))) {
            throw new ForbiddenException("No podes acceder a comprobantes de otro cliente");
        }
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] pdf, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping
    public List<Pedido> getAllPedidos(
            @RequestHeader(value = "X-User-Rol", required = false) String rol,
            @RequestHeader(value = "X-User-ClienteId", required = false) Integer tokenClienteId) {
        if (isCliente(rol)) {
            if (tokenClienteId == null) {
                return List.of();
            }
            return pedidoService.searchPedidos(null, null, tokenClienteId);
        }
        return pedidoService.getAllPedidos();
    }

    @GetMapping("/search")
    public List<Pedido> searchPedidos(
            @RequestParam(required = false) String clienteNombre,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Integer clienteId,
            @RequestHeader(value = "X-User-Rol", required = false) String rol,
            @RequestHeader(value = "X-User-ClienteId", required = false) Integer tokenClienteId) {
        EstadoPedido estadoEnum = (estado != null && !estado.isBlank())
                ? EstadoPedido.valueOf(estado.toUpperCase())
                : null;
        Integer effectiveClienteId = clienteId;
        if (isCliente(rol)) {
            if (tokenClienteId == null) {
                return List.of();
            }
            effectiveClienteId = tokenClienteId;
        }
        return pedidoService.searchPedidos(clienteNombre, estadoEnum, effectiveClienteId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pedido> getPedidoById(
            @PathVariable String id,
            @RequestHeader(value = "X-User-Rol", required = false) String rol,
            @RequestHeader(value = "X-User-ClienteId", required = false) Integer tokenClienteId) {
        Pedido pedido = pedidoService.getPedidoById(id);
        if (isCliente(rol)) {
            if (pedido.getCliente() == null || tokenClienteId == null
                    || !pedido.getCliente().getId().equals(tokenClienteId)) {
                throw new ForbiddenException("No podes acceder a pedidos de otro cliente");
            }
        }
        return ResponseEntity.ok(pedido);
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<Pedido> updateEstado(
            @PathVariable String id,
            @RequestBody @Valid EstadoUpdateRequest request,
            @RequestHeader(value = "X-User-Rol", required = false) String rol,
            @RequestHeader(value = "X-User-Nombre", required = false) String userNombre,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail) {
        if (!isAdmin(rol)) {
            throw new ForbiddenException("No tenes permisos para cambiar el estado del pedido");
        }
        return ResponseEntity.ok(pedidoService.updateEstado(
                id, request.getEstado(), actor(userNombre, userEmail)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePedido(
            @PathVariable String id,
            @RequestHeader(value = "X-User-Rol", required = false) String rol,
            @RequestHeader(value = "X-User-Nombre", required = false) String userNombre,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail) {
        if (!isAdmin(rol)) {
            throw new ForbiddenException("No tenes permisos para borrar pedidos");
        }
        pedidoService.getPedidoById(id);
        pedidoService.deletePedido(id, actor(userNombre, userEmail));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/auditoria")
    public List<isi.dan.ms.pedidos.modelo.AuditLog> getAuditoria(
            @PathVariable String id,
            @RequestHeader(value = "X-User-Rol", required = false) String rol,
            @RequestHeader(value = "X-User-ClienteId", required = false) Integer tokenClienteId) {
        Pedido pedido = pedidoService.getPedidoById(id);
        if (isCliente(rol)) {
            if (pedido.getCliente() == null || tokenClienteId == null
                    || !pedido.getCliente().getId().equals(tokenClienteId)) {
                throw new ForbiddenException("No podes acceder a la auditoria de otro cliente");
            }
        }
        return pedido.getAuditoria() == null ? List.of() : pedido.getAuditoria();
    }

    @GetMapping("/{id}/entregas")
    public List<Entrega> getEntregas(
            @PathVariable String id,
            @RequestHeader(value = "X-User-Rol", required = false) String rol,
            @RequestHeader(value = "X-User-ClienteId", required = false) Integer tokenClienteId) {
        Pedido pedido = pedidoService.getPedidoById(id);
        if (isCliente(rol)) {
            if (pedido.getCliente() == null || tokenClienteId == null
                    || !pedido.getCliente().getId().equals(tokenClienteId)) {
                throw new ForbiddenException("No podes acceder a entregas de otro cliente");
            }
        }
        return pedidoService.getEntregas(id);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> getPedidoPdf(
            @PathVariable String id,
            @RequestHeader(value = "X-User-Rol", required = false) String rol,
            @RequestHeader(value = "X-User-ClienteId", required = false) Integer tokenClienteId) {
        Pedido pedido = pedidoService.getPedidoById(id);
        ensureOwnership(pedido, rol, tokenClienteId);
        byte[] pdf = pedidoPdfService.generarPedido(pedido);
        return pdfResponse(pdf, "pedido-" + pedido.getNumeroPedido() + ".pdf");
    }

    @GetMapping("/{id}/entregas/{entregaId}/pdf")
    public ResponseEntity<byte[]> getRemitoPdf(
            @PathVariable String id,
            @PathVariable String entregaId,
            @RequestHeader(value = "X-User-Rol", required = false) String rol,
            @RequestHeader(value = "X-User-ClienteId", required = false) Integer tokenClienteId) {
        Pedido pedido = pedidoService.getPedidoById(id);
        ensureOwnership(pedido, rol, tokenClienteId);
        Entrega entrega = pedidoService.getEntrega(id, entregaId);
        byte[] pdf = pedidoPdfService.generarRemito(pedido, entrega);
        return pdfResponse(pdf, "remito-" + entrega.getNumeroRemito() + ".pdf");
    }

    @PostMapping("/{id}/entregas")
    public ResponseEntity<Pedido> registrarEntrega(
            @PathVariable String id,
            @RequestBody @Valid EntregaRequest request,
            @RequestHeader(value = "X-User-Rol", required = false) String rol,
            @RequestHeader(value = "X-User-Nombre", required = false) String userNombre,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail) {
        if (!isAdmin(rol)) {
            throw new ForbiddenException("No tenes permisos para registrar entregas");
        }
        return ResponseEntity.ok(pedidoService.registrarEntrega(
                id, request, actor(userNombre, userEmail)));
    }

    @PostMapping("/{id}/reintentar-reserva")
    public ResponseEntity<Pedido> reintentarReserva(
            @PathVariable String id,
            @RequestHeader(value = "X-User-Rol", required = false) String rol,
            @RequestHeader(value = "X-User-Nombre", required = false) String userNombre,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail) {
        if (!isAdmin(rol)) {
            throw new ForbiddenException("No tenes permisos para reintentar la reserva de stock");
        }
        return ResponseEntity.ok(pedidoService.reintentarReserva(id, actor(userNombre, userEmail)));
    }

    private boolean isCliente(String rol) {
        return "CLIENTE".equalsIgnoreCase(rol);
    }

    private boolean isAdmin(String rol) {
        return "ADMIN".equalsIgnoreCase(rol);
    }
}

