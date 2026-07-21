package isi.dan.msclientes.servicios;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import isi.dan.msclientes.dao.ClienteRepository;
import isi.dan.msclientes.model.Cliente;

@Service
public class ClienteService {
    
    @Autowired
    private ClienteRepository clienteRepository;

    public List<Cliente> findAll() {
        return clienteRepository.findAll();
    }

    public Optional<Cliente> findById(Integer id) {
        return clienteRepository.findById(id);
    }

    public Cliente save(Cliente cliente) {
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
        return clienteRepository.save(existente);
    }

    public void deleteById(Integer id) {
        clienteRepository.deleteById(id);
    }

    public boolean checkSaldo(Integer idCliente, BigDecimal monto) {
        Cliente cliente = clienteRepository.findById(idCliente)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado: " + idCliente));
        return cliente.getMaximoDescubierto().compareTo(monto) >= 0;
    }

    public BigDecimal getMaximoDescubierto(Integer idCliente) {
        Cliente cliente = clienteRepository.findById(idCliente)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado: " + idCliente));
        return cliente.getMaximoDescubierto();
    }
}
