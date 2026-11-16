package isi.dan.msclientes.servicios;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import isi.dan.msclientes.dao.ClienteRepository;
import isi.dan.msclientes.dao.PagoRepository;
import isi.dan.msclientes.model.Cliente;
import isi.dan.msclientes.model.Pago;

@Service
public class ClienteService {

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private PagoRepository pagoRepository;

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
        if (cliente.getSaldoInicial() == null) {
            cliente.setSaldoInicial(BigDecimal.ZERO);
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
    if (nuevo.getSaldoInicial() != null) {
        existente.setSaldoInicial(nuevo.getSaldoInicial());
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

    /**
     * El monto recibido es el consumo bruto de pedidos (activos + entregados).
     * El credito disponible se amplia con los pagos registrados:
     * consumo <= maximoDescubierto + pagos.
     */
    public boolean checkSaldo(Integer idCliente, BigDecimal monto) {
        if (monto == null) {
            return false;
        }
        Cliente cliente = clienteRepository.findByIdAndHabilitadoTrue(idCliente)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado: " + idCliente));
        BigDecimal credito = maximoDescubiertoDe(cliente)
                .add(totalPagos(idCliente))
                .subtract(saldoInicialDe(cliente));
        return credito.compareTo(monto) >= 0;
    }

    public BigDecimal getMaximoDescubierto(Integer idCliente) {
        Cliente cliente = clienteRepository.findByIdAndHabilitadoTrue(idCliente)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado: " + idCliente));
        return maximoDescubiertoDe(cliente);
    }

    public BigDecimal getTotalPagos(Integer idCliente) {
        return totalPagos(idCliente);
    }

    private BigDecimal totalPagos(Integer idCliente) {
        return pagoRepository.findByClienteIdAndHabilitadoTrueOrderByFechaDesc(idCliente).stream()
                .map(Pago::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal maximoDescubiertoDe(Cliente cliente) {
        return cliente.getMaximoDescubierto() == null ? BigDecimal.ZERO : cliente.getMaximoDescubierto();
    }

    private BigDecimal saldoInicialDe(Cliente cliente) {
        return cliente.getSaldoInicial() == null ? BigDecimal.ZERO : cliente.getSaldoInicial();
    }
}
