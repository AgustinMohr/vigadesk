package isi.dan.msclientes.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import dan.common.web.ParametrosConsulta;
import isi.dan.msclientes.aop.LogExecutionTime;
import isi.dan.msclientes.dto.ContextoPedidoDTO;
import isi.dan.msclientes.dto.ImportResult;
import isi.dan.msclientes.exception.ClienteNotFoundException;
import isi.dan.msclientes.exception.ForbiddenException;
import isi.dan.msclientes.model.Cliente;
import isi.dan.msclientes.servicios.ClienteImportService;
import isi.dan.msclientes.servicios.ClienteService;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    @Autowired
    private ClienteService clienteService;

    @Autowired
    private ClienteImportService clienteImportService;

    @GetMapping
    @LogExecutionTime
    public ResponseEntity<List<Cliente>> getAll(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestHeader(value = "X-User-Rol", required = false) String rol,
            @RequestHeader(value = "X-User-ClienteId", required = false) Integer clienteId) {
        if (isCliente(rol)) {
            List<Cliente> propio = clienteId == null ? List.of()
                    : clienteService.findById(clienteId).map(cliente -> List.of(cliente)).orElseGet(List::of);
            return conTotal(propio, propio.size());
        }
        Page<Cliente> resultado = clienteService.findAll(page, size);
        return conTotal(resultado.getContent(), resultado.getTotalElements());
    }

    private static <T> ResponseEntity<List<T>> conTotal(List<T> contenido, long total) {
        return ResponseEntity.ok().header(ParametrosConsulta.CABECERA_TOTAL, String.valueOf(total)).body(contenido);
    }

    @GetMapping("/{id}")
    @LogExecutionTime
    public ResponseEntity<Cliente> getById(
            @PathVariable Integer id,
            @RequestHeader(value = "X-User-Rol", required = false) String rol,
            @RequestHeader(value = "X-User-ClienteId", required = false) Integer clienteId) throws ClienteNotFoundException {
        Cliente cliente = clienteService.findById(id)
                .orElseThrow(() -> new ClienteNotFoundException("Cliente " + id + " no encontrado"));
        ensureOwnership(id, rol, clienteId);
        return ResponseEntity.ok(cliente);
    }

    @PostMapping
    @LogExecutionTime
    public Cliente create(@RequestBody @Validated Cliente cliente) {
        return clienteService.save(cliente);
    }

    @PostMapping("/import")
    @LogExecutionTime
    public ResponseEntity<ImportResult> importarCsv(@RequestParam("file") MultipartFile file)
            throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo CSV es obligatorio");
        }
        return ResponseEntity.ok(clienteImportService.importar(file.getInputStream()));
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
        Cliente cliente = clienteService.findById(id)
                .orElseThrow(() -> new ClienteNotFoundException("Cliente " + id + " no encontrado para borrar"));
        clienteService.deleteById(cliente.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/saldo")
    @LogExecutionTime
    public ResponseEntity<Boolean> checkSaldo(
            @PathVariable Integer id,
            @RequestParam BigDecimal monto,
            @RequestHeader(value = "X-User-Rol", required = false) String rol,
            @RequestHeader(value = "X-User-ClienteId", required = false) Integer clienteId) throws ClienteNotFoundException {
        Cliente cliente = clienteService.findById(id)
                .orElseThrow(() -> new ClienteNotFoundException("Cliente " + id + " no encontrado"));
        ensureOwnership(id, rol, clienteId);
        return ResponseEntity.ok(clienteService.checkSaldo(cliente.getId(), monto));
    }

    @GetMapping("/{id}/maximo-descubierto")
    @LogExecutionTime
    public ResponseEntity<BigDecimal> getMaximoDescubierto(
            @PathVariable Integer id,
            @RequestHeader(value = "X-User-Rol", required = false) String rol,
            @RequestHeader(value = "X-User-ClienteId", required = false) Integer clienteId) throws ClienteNotFoundException {
        Cliente cliente = clienteService.findById(id)
                .orElseThrow(() -> new ClienteNotFoundException("Cliente " + id + " no encontrado"));
        ensureOwnership(id, rol, clienteId);
        return ResponseEntity.ok(clienteService.getMaximoDescubierto(cliente.getId()));
    }

    @GetMapping("/{id}/contexto-pedido")
    @LogExecutionTime
    public ResponseEntity<ContextoPedidoDTO> getContextoPedido(
            @PathVariable Integer id,
            @RequestParam(required = false) Integer obraId) throws ClienteNotFoundException {
        return ResponseEntity.ok(clienteService.contextoPedido(id, obraId)
                .orElseThrow(() -> new ClienteNotFoundException("Cliente " + id + " no encontrado")));
    }

    private boolean isCliente(String rol) {
        return "CLIENTE".equalsIgnoreCase(rol);
    }

    private void ensureOwnership(Integer resourceId, String rol, Integer tokenClienteId) {
        if (isCliente(rol) && (tokenClienteId == null || !resourceId.equals(tokenClienteId))) {
            throw new ForbiddenException("No podes acceder a datos de otro cliente");
        }
    }
}
