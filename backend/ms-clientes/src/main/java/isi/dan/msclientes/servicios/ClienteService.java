package isi.dan.msclientes.servicios;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import dan.common.web.ParametrosConsulta;
import isi.dan.msclientes.dao.ClienteRepository;
import isi.dan.msclientes.dao.ObraRepository;
import isi.dan.msclientes.dao.PagoRepository;
import isi.dan.msclientes.dto.ContextoPedidoDTO;
import isi.dan.msclientes.exception.ClienteNotFoundException;
import isi.dan.msclientes.model.Cliente;
import isi.dan.msclientes.model.Pago;

@Service
public class ClienteService {

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private PagoRepository pagoRepository;

    @Autowired
    private ObraRepository obraRepository;

    @Value("${cliente.maximo-descubierto.default:10000}")
    private BigDecimal maximoDescubiertoDefault;

    /** Listado paginado (orden por id) de registros activos. */
    public Page<Cliente> findAll(Integer page, Integer size) {
        return clienteRepository.findByHabilitadoTrue(PageRequest.of(ParametrosConsulta.pagina(page),
                ParametrosConsulta.tamanio(size), Sort.by("id")));
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
                .orElseThrow(() -> new ClienteNotFoundException("Cliente " + idCliente + " no encontrado"));
        BigDecimal credito = maximoDescubiertoDe(cliente)
                .add(totalPagos(idCliente))
                .subtract(saldoInicialDe(cliente));
        return credito.compareTo(monto) >= 0;
    }

    public BigDecimal getMaximoDescubierto(Integer idCliente) {
        Cliente cliente = clienteRepository.findByIdAndHabilitadoTrue(idCliente)
                .orElseThrow(() -> new ClienteNotFoundException("Cliente " + idCliente + " no encontrado"));
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

    /**
     * Datos del cliente y, si se indica, de la obra, para que ms-pedidos valide el pedido.
     * Vacio si el cliente no existe o esta dado de baja.
     */
    public Optional<ContextoPedidoDTO> contextoPedido(Integer idCliente, Integer obraId) {
        return clienteRepository.findByIdAndHabilitadoTrue(idCliente).map(cliente -> {
            ContextoPedidoDTO dto = new ContextoPedidoDTO();
            dto.setClienteId(cliente.getId());
            dto.setClienteNombre(cliente.getNombre());
            dto.setClienteCorreoElectronico(cliente.getCorreoElectronico());
            dto.setClienteCuit(cliente.getCuit());
            if (obraId != null) {
                obraRepository.findByIdAndHabilitadoTrue(obraId).ifPresent(obra -> {
                    dto.setObraId(obra.getId());
                    dto.setObraDireccion(obra.getDireccion());
                    dto.setObraEstado(obra.getEstado() == null ? null : obra.getEstado().name());
                    dto.setObraClienteId(obra.getCliente() == null ? null : obra.getCliente().getId());
                });
            }
            return dto;
        });
    }
}
