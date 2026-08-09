package isi.dan.msclientes.servicios;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import isi.dan.msclientes.dao.ClienteRepository;
import isi.dan.msclientes.dao.ObraRepository;
import isi.dan.msclientes.model.Cliente;
import isi.dan.msclientes.model.EstadoObra;
import isi.dan.msclientes.model.Obra;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class ObraService {
    
    @Autowired
    private ObraRepository obraRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    public List<Obra> findAll() {
        return obraRepository.findByHabilitadoTrue();
    }

    public Optional<Obra> findById(Integer id) {
        return obraRepository.findByIdAndHabilitadoTrue(id);
    }

    @Transactional
    public Obra save(Obra obra) {
        if (obra.getCliente() != null) {
            Integer clienteId = obra.getCliente().getId();
            Cliente cliente = clienteRepository.findByIdAndHabilitadoTrue(clienteId)
                    .orElseThrow(() -> new RuntimeException("Cliente no encontrado: " + clienteId));
            obra.setCliente(cliente);
            obra.setEstado(resolverEstadoInicial(cliente));
        }
        return obraRepository.save(obra);
    }

    public Obra update(Obra obra) {
        return obraRepository.save(obra);
    }

    public void deleteById(Integer id) {
        obraRepository.findByIdAndHabilitadoTrue(id).ifPresent(obra -> {
            obra.setHabilitado(false);
            obra.setFechaBorrado(Instant.now());
            obraRepository.save(obra);
        });
    }

    public List<Obra> findByClienteId(Integer clienteId) {
        return obraRepository.findByClienteIdAndHabilitadoTrue(clienteId);
    }

    public List<Obra> findByClienteIsNull() {
        return obraRepository.findByClienteIsNullAndHabilitadoTrue();
    }

    @Transactional
    public Obra asignarCliente(Integer obraId, Integer clienteId) {
        Obra obra = obraRepository.findByIdAndHabilitadoTrue(obraId)
                .orElseThrow(() -> new RuntimeException("Obra no encontrada: " + obraId));
        Cliente cliente = clienteRepository.findByIdAndHabilitadoTrue(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado: " + clienteId));
        obra.setCliente(cliente);
        obra.setEstado(resolverEstadoInicial(cliente));
        return obraRepository.save(obra);
    }

    @Transactional
    public Obra cambiarEstado(Integer obraId, EstadoObra nuevoEstado) {
        Obra obra = obraRepository.findByIdAndHabilitadoTrue(obraId)
                .orElseThrow(() -> new RuntimeException("Obra no encontrada: " + obraId));

        if (obra.getCliente() == null) {
            throw new RuntimeException("La obra debe tener un cliente asignado para cambiar su estado");
        }

        switch (nuevoEstado) {
            case HABILITADA:
                if (!puedeHabilitar(obra.getCliente().getId())) {
                    throw new RuntimeException("No se puede habilitar la obra: se alcanzo el maximo de obras activas para el cliente");
                }
                obra.setEstado(EstadoObra.HABILITADA);
                break;

            case PENDIENTE:
                obra.setEstado(EstadoObra.PENDIENTE);
                break;

            case FINALIZADA:
                obra.setEstado(EstadoObra.FINALIZADA);
                obraRepository.save(obra);
                habilitarProximaPendiente(obra.getCliente().getId());
                return obra;

            default:
                throw new RuntimeException("Estado no valido: " + nuevoEstado);
        }

        return obraRepository.save(obra);
    }

    private EstadoObra resolverEstadoInicial(Cliente cliente) {
        if (puedeHabilitar(cliente.getId())) {
            return EstadoObra.HABILITADA;
        }
        return EstadoObra.PENDIENTE;
    }

    private boolean puedeHabilitar(Integer clienteId) {
        Cliente cliente = clienteRepository.findByIdAndHabilitadoTrue(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado: " + clienteId));
        Integer max = cliente.getMaxObrasActivas();
        if (max == null) {
            return true;
        }
        long habilitadas = obraRepository.countByClienteIdAndEstadoAndHabilitadoTrue(clienteId, EstadoObra.HABILITADA);
        return habilitadas < max;
    }

    private void habilitarProximaPendiente(Integer clienteId) {
        if (!puedeHabilitar(clienteId)) {
            return;
        }
        List<Obra> pendientes = obraRepository.findByClienteIdAndEstadoAndHabilitadoTrueOrderByIdAsc(clienteId, EstadoObra.PENDIENTE);
        if (!pendientes.isEmpty()) {
            Obra proxima = pendientes.get(0);
            proxima.setEstado(EstadoObra.HABILITADA);
            obraRepository.save(proxima);
        }
    }
}

