package isi.dan.msclientes.servicios;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import isi.dan.msclientes.dao.ClienteRepository;
import isi.dan.msclientes.dao.ObraRepository;
import isi.dan.msclientes.model.Cliente;
import isi.dan.msclientes.model.Obra;

import java.util.List;
import java.util.Optional;

@Service
public class ObraService {
    
    @Autowired
    private ObraRepository obraRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    public List<Obra> findAll() {
        return obraRepository.findAll();
    }

    public Optional<Obra> findById(Integer id) {
        return obraRepository.findById(id);
    }

    public Obra save(Obra obra) {
        return obraRepository.save(obra);
    }

    public Obra update(Obra obra) {
        return obraRepository.save(obra);
    }

    public void deleteById(Integer id) {
        obraRepository.deleteById(id);
    }

    public List<Obra> findByClienteId(Integer clienteId) {
        return obraRepository.findByClienteId(clienteId);
    }

    public List<Obra> findByClienteIsNull() {
        return obraRepository.findByClienteIsNull();
    }

    // Asigna un cliente existente a una obra existente (asociación ManyToOne)
    public Obra asignarCliente(Integer obraId, Integer clienteId) {
        Obra obra = obraRepository.findById(obraId)
                .orElseThrow(() -> new RuntimeException("Obra no encontrada: " + obraId));
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado: " + clienteId));
        obra.setCliente(cliente);
        return obraRepository.save(obra);
    }
}

