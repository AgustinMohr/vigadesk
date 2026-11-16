package isi.dan.msclientes.servicios;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import isi.dan.msclientes.dao.ClienteRepository;
import isi.dan.msclientes.dao.PagoRepository;
import isi.dan.msclientes.exception.ClienteNotFoundException;
import isi.dan.msclientes.model.Cliente;
import isi.dan.msclientes.model.Pago;

@Service
public class PagoService {

    @Autowired
    private PagoRepository pagoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    public List<Pago> findByCliente(Integer clienteId) {
        return pagoRepository.findByClienteIdAndHabilitadoTrueOrderByFechaDesc(clienteId);
    }

    public BigDecimal totalPagos(Integer clienteId) {
        return findByCliente(clienteId).stream()
                .map(Pago::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Pago registrar(Integer clienteId, Pago pago) throws ClienteNotFoundException {
        return registrar(clienteId, pago, "sistema");
    }

    public Pago registrar(Integer clienteId, Pago pago, String actor) throws ClienteNotFoundException {
        if (pago.getMonto() == null || pago.getMonto().signum() <= 0) {
            throw new IllegalArgumentException("El monto del pago debe ser mayor a cero");
        }
        if (pago.getMetodoPago() == null || pago.getMetodoPago().isBlank()) {
            throw new IllegalArgumentException("El metodo de pago es obligatorio");
        }

        Cliente cliente = clienteRepository.findByIdAndHabilitadoTrue(clienteId)
                .orElseThrow(() -> new ClienteNotFoundException("Cliente " + clienteId + " no encontrado"));

        pago.setId(null);
        pago.setCliente(cliente);
        pago.setFecha(Instant.now());
        pago.setRegistradoPor(actor);
        pago.setHabilitado(true);
        pago.setFechaBorrado(null);
        return pagoRepository.save(pago);
    }

    public void delete(Integer clienteId, Integer pagoId) {
        Pago pago = pagoRepository.findByIdAndHabilitadoTrue(pagoId)
                .filter(p -> p.getCliente() != null && clienteId.equals(p.getCliente().getId()))
                .orElseThrow(() -> new IllegalArgumentException("Pago " + pagoId + " no encontrado"));
        pago.setHabilitado(false);
        pago.setFechaBorrado(Instant.now());
        pagoRepository.save(pago);
    }
}
