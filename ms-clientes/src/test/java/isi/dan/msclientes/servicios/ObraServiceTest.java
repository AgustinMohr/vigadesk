package isi.dan.msclientes.servicios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import isi.dan.msclientes.dao.ClienteRepository;
import isi.dan.msclientes.dao.ObraRepository;
import isi.dan.msclientes.model.Cliente;
import isi.dan.msclientes.model.Obra;

@ExtendWith(MockitoExtension.class)
class ObraServiceTest {

    @Mock
    private ObraRepository obraRepository;

    @Mock
    private ClienteRepository clienteRepository;

    @InjectMocks
    private ObraService obraService;

    private Obra obra;
    private Cliente cliente;

    @BeforeEach
    void setUp() {
        cliente = new Cliente();
        cliente.setId(1);
        cliente.setNombre("Test Cliente");

        obra = new Obra();
        obra.setId(1);
        obra.setDireccion("Test Obra");
        obra.setPresupuesto(BigDecimal.valueOf(1000));
        obra.setCliente(cliente);
    }

    @Test
    void findAll_ShouldReturnAllObras() {
        when(obraRepository.findAll()).thenReturn(Collections.singletonList(obra));

        List<Obra> result = obraService.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDireccion()).isEqualTo("Test Obra");
    }

    @Test
    void findById_WhenExists_ShouldReturnObra() {
        when(obraRepository.findById(1)).thenReturn(Optional.of(obra));

        Optional<Obra> result = obraService.findById(1);

        assertThat(result).isPresent();
        assertThat(result.get().getDireccion()).isEqualTo("Test Obra");
    }

    @Test
    void findById_WhenNotExists_ShouldReturnEmpty() {
        when(obraRepository.findById(99)).thenReturn(Optional.empty());

        Optional<Obra> result = obraService.findById(99);

        assertThat(result).isEmpty();
    }

    @Test
    void save_ShouldReturnSavedObra() {
        when(obraRepository.save(any(Obra.class))).thenReturn(obra);

        Obra result = obraService.save(obra);

        assertThat(result).isNotNull();
        assertThat(result.getDireccion()).isEqualTo("Test Obra");
    }

    @Test
    void update_ShouldReturnUpdatedObra() {
        when(obraRepository.save(any(Obra.class))).thenReturn(obra);

        Obra result = obraService.update(obra);

        assertThat(result).isNotNull();
        assertThat(result.getDireccion()).isEqualTo("Test Obra");
    }

    @Test
    void deleteById_ShouldDeleteObra() {
        obraService.deleteById(1);

        verify(obraRepository).deleteById(1);
    }

    @Test
    void findByClienteId_ShouldReturnObras() {
        when(obraRepository.findByClienteId(1)).thenReturn(Collections.singletonList(obra));

        List<Obra> result = obraService.findByClienteId(1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCliente().getId()).isEqualTo(1);
    }

    @Test
    void findByClienteIsNull_ShouldReturnObrasSinAsignar() {
        Obra obraSinCliente = new Obra();
        obraSinCliente.setId(2);
        obraSinCliente.setDireccion("Obra sin cliente");
        when(obraRepository.findByClienteIsNull()).thenReturn(Collections.singletonList(obraSinCliente));

        List<Obra> result = obraService.findByClienteIsNull();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDireccion()).isEqualTo("Obra sin cliente");
    }

    @Test
    void asignarCliente_WhenBothExist_ShouldAssignAndReturn() {
        when(obraRepository.findById(1)).thenReturn(Optional.of(obra));
        when(clienteRepository.findById(1)).thenReturn(Optional.of(cliente));
        when(obraRepository.save(any(Obra.class))).thenReturn(obra);

        Obra result = obraService.asignarCliente(1, 1);

        assertThat(result).isNotNull();
        assertThat(result.getCliente().getId()).isEqualTo(1);
    }

    @Test
    void asignarCliente_WhenObraNotExists_ShouldThrowRuntimeException() {
        when(obraRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> obraService.asignarCliente(99, 1))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Obra no encontrada: 99");
    }

    @Test
    void asignarCliente_WhenClienteNotExists_ShouldThrowRuntimeException() {
        when(obraRepository.findById(1)).thenReturn(Optional.of(obra));
        when(clienteRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> obraService.asignarCliente(1, 99))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cliente no encontrado: 99");
    }
}
