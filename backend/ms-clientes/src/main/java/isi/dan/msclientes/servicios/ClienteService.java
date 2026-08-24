package isi.dan.msclientes.servicios;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import isi.dan.msclientes.dao.ClienteRepository;
import isi.dan.msclientes.model.Cliente;

@Service
public class ClienteService {

    @Autowired
    private ClienteRepository clienteRepository;

    @Value("${cliente.maximo-descubierto.default:10000}")
    private BigDecimal maximoDescubiertoDefault;

    public List<Cliente> findAll() {
        return clienteRepository.findByHabilitadoTrue();
    }

    public Optional<Cliente> findById(Integer id) {
        return clienteRepository.findByIdAndHabilitadoTrue(id);
    }

    public Cliente save(Cliente cliente) {
        if (cliente.getMaximoDescubierto() == null) {
            cliente.setMaximoDescubierto(maximoDescubiertoDefault);
        }
        return clienteRepository.save(cliente);
    }

    public Cliente update(Cliente existente, Cliente nuevo) {
	if (nuevo.getNombre() != null) {
        existente.setNombre(nuevo.getNombre());
    }
    if (nuevo.getCorreoElectronico() != null) {
        existente.setCorreoElectronico(nuevo.getCorreoElectronico());
    }
    if (nuevo.getCuit() != null) {
        existente.setCuit(nuevo.getCuit());
    }
    if (nuevo.getMaximoDescubierto() != null) {
        existente.setMaximoDescubierto(nuevo.getMaximoDescubierto());
    }
    if (nuevo.getMaxObrasActivas() != null) {
        existente.setMaxObrasActivas(nuevo.getMaxObrasActivas());
    }
        return clienteRepository.save(existente);
    }

    public void deleteById(Integer id) {
        clienteRepository.findByIdAndHabilitadoTrue(id).ifPresent(cliente -> {
            cliente.setHabilitado(false);
            cliente.setFechaBorrado(Instant.now());
            clienteRepository.save(cliente);
        });
    }

    public boolean checkSaldo(Integer idCliente, BigDecimal monto) {
        Cliente cliente = clienteRepository.findByIdAndHabilitadoTrue(idCliente)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado: " + idCliente));
        return cliente.getMaximoDescubierto().compareTo(monto) >= 0;
    }

    public BigDecimal getMaximoDescubierto(Integer idCliente) {
        Cliente cliente = clienteRepository.findByIdAndHabilitadoTrue(idCliente)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado: " + idCliente));
        return cliente.getMaximoDescubierto();
    }
}
