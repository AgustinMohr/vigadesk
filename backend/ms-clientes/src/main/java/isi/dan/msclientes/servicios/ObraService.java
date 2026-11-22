package isi.dan.msclientes.servicios;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import isi.dan.msclientes.dao.ClienteRepository;
import isi.dan.msclientes.dao.ObraRepository;
import dan.common.error.SolicitudNoProcesableException;
import isi.dan.msclientes.exception.ClienteNotFoundException;
import isi.dan.msclientes.exception.ObraNotFoundException;
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
                    .orElseThrow(() -> new ClienteNotFoundException("Cliente " + clienteId + " no encontrado"));
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
                .orElseThrow(() -> new ObraNotFoundException("Obra " + obraId + " no encontrada"));
        Cliente cliente = clienteRepository.findByIdAndHabilitadoTrue(clienteId)
                .orElseThrow(() -> new ClienteNotFoundException("Cliente " + clienteId + " no encontrado"));
        obra.setCliente(cliente);
        obra.setEstado(resolverEstadoInicial(cliente));
        return obraRepository.save(obra);
    }

    @Transactional
    public Obra cambiarEstado(Integer obraId, EstadoObra nuevoEstado) {
        Obra obra = obraRepository.findByIdAndHabilitadoTrue(obraId)
                .orElseThrow(() -> new ObraNotFoundException("Obra " + obraId + " no encontrada"));

        if (obra.getCliente() == null) {
            throw new SolicitudNoProcesableException("La obra debe tener un cliente asignado para cambiar su estado");
        }

        switch (nuevoEstado) {
            case HABILITADA:
                obra.setEstado(EstadoObra.HABILITADA);
                break;

            case PENDIENTE:
                obra.setEstado(EstadoObra.PENDIENTE);
                break;

            case FINALIZADA:
                obra.setEstado(EstadoObra.FINALIZADA);
                break;

            default:
                throw new IllegalArgumentException("Estado no valido: " + nuevoEstado);
        }

        return obraRepository.save(obra);
    }

    private EstadoObra resolverEstadoInicial(Cliente cliente) {
        return EstadoObra.HABILITADA;
    }
}

