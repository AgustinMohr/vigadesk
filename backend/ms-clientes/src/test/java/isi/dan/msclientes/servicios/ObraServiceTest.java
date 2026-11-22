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

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import isi.dan.msclientes.dao.ClienteRepository;
import isi.dan.msclientes.dao.ObraRepository;
import isi.dan.msclientes.model.Cliente;
import isi.dan.msclientes.model.EstadoObra;
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
        cliente.setMaximoDescubierto(BigDecimal.valueOf(100000));

        obra = new Obra();
        obra.setId(1);
        obra.setDireccion("Test Obra");
        obra.setPresupuesto(BigDecimal.valueOf(1000));
        obra.setCliente(cliente);
    }

    @Test
    void findAll_ShouldReturnAllObras() {
        when(obraRepository.findByHabilitadoTrue(any(Pageable.class))).thenReturn(new PageImpl<>(Collections.singletonList(obra)));

        List<Obra> result = obraService.findAll(null, null).getContent();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDireccion()).isEqualTo("Test Obra");
    }

    @Test
    void findById_WhenExists_ShouldReturnObra() {
        when(obraRepository.findByIdAndHabilitadoTrue(1)).thenReturn(Optional.of(obra));

        Optional<Obra> result = obraService.findById(1);

        assertThat(result).isPresent();
        assertThat(result.get().getDireccion()).isEqualTo("Test Obra");
    }

    @Test
    void findById_WhenNotExists_ShouldReturnEmpty() {
        when(obraRepository.findByIdAndHabilitadoTrue(99)).thenReturn(Optional.empty());

        Optional<Obra> result = obraService.findById(99);

        assertThat(result).isEmpty();
    }

    @Test
    void save_WithoutCliente_ShouldReturnSavedObra() {
        obra.setCliente(null);
        obra.setEstado(null);
        when(obraRepository.save(any(Obra.class))).thenReturn(obra);

        Obra result = obraService.save(obra);

        assertThat(result).isNotNull();
        assertThat(result.getDireccion()).isEqualTo("Test Obra");
        assertThat(result.getEstado()).isNull();
    }

    @Test
    void save_WithCliente_ShouldSetHabilitada() {
        when(clienteRepository.findByIdAndHabilitadoTrue(1)).thenReturn(Optional.of(cliente));
        when(obraRepository.save(any(Obra.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Obra result = obraService.save(obra);

        assertThat(result.getEstado()).isEqualTo(EstadoObra.HABILITADA);
    }

    @Test
    void save_WithClienteInexistente_ShouldThrow() {
        obra.setCliente(new Cliente());
        obra.getCliente().setId(99);
        when(clienteRepository.findByIdAndHabilitadoTrue(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> obraService.save(obra))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cliente 99 no encontrado");
    }

    @Test
    void update_ShouldReturnUpdatedObra() {
        when(obraRepository.save(any(Obra.class))).thenReturn(obra);

        Obra result = obraService.update(obra);

        assertThat(result).isNotNull();
        assertThat(result.getDireccion()).isEqualTo("Test Obra");
    }

    @Test
    void deleteById_ShouldSoftDeleteObra() {
        when(obraRepository.findByIdAndHabilitadoTrue(1)).thenReturn(Optional.of(obra));

        obraService.deleteById(1);

        assertThat(obra.isHabilitado()).isFalse();
        assertThat(obra.getFechaBorrado()).isNotNull();
        verify(obraRepository).save(obra);
    }

    @Test
    void deleteById_WhenAlreadyDeleted_ShouldDoNothing() {
        when(obraRepository.findByIdAndHabilitadoTrue(1)).thenReturn(Optional.empty());

        obraService.deleteById(1);

        verify(obraRepository, org.mockito.Mockito.never()).save(any(Obra.class));
    }

    @Test
    void findByClienteId_ShouldReturnObras() {
        when(obraRepository.findByClienteIdAndHabilitadoTrue(1)).thenReturn(Collections.singletonList(obra));

        List<Obra> result = obraService.findByClienteId(1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCliente().getId()).isEqualTo(1);
    }

    @Test
    void findByClienteIsNull_ShouldReturnObrasSinAsignar() {
        Obra obraSinCliente = new Obra();
        obraSinCliente.setId(2);
        obraSinCliente.setDireccion("Obra sin cliente");
        when(obraRepository.findByClienteIsNullAndHabilitadoTrue()).thenReturn(Collections.singletonList(obraSinCliente));

        List<Obra> result = obraService.findByClienteIsNull();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDireccion()).isEqualTo("Obra sin cliente");
    }

    @Test
    void asignarCliente_WhenBothExist_ShouldSetHabilitada() {
        when(obraRepository.findByIdAndHabilitadoTrue(1)).thenReturn(Optional.of(obra));
        when(clienteRepository.findByIdAndHabilitadoTrue(1)).thenReturn(Optional.of(cliente));
        when(obraRepository.save(any(Obra.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Obra result = obraService.asignarCliente(1, 1);

        assertThat(result).isNotNull();
        assertThat(result.getCliente().getId()).isEqualTo(1);
        assertThat(result.getEstado()).isEqualTo(EstadoObra.HABILITADA);
    }

    @Test
    void asignarCliente_WhenObraNotExists_ShouldThrowRuntimeException() {
        when(obraRepository.findByIdAndHabilitadoTrue(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> obraService.asignarCliente(99, 1))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Obra 99 no encontrada");
    }

    @Test
    void asignarCliente_WhenClienteNotExists_ShouldThrowRuntimeException() {
        when(obraRepository.findByIdAndHabilitadoTrue(1)).thenReturn(Optional.of(obra));
        when(clienteRepository.findByIdAndHabilitadoTrue(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> obraService.asignarCliente(1, 99))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cliente 99 no encontrado");
    }

    @Test
    void cambiarEstado_ToFinalizada_ShouldSetFinalizada() {
        obra.setEstado(EstadoObra.HABILITADA);
        when(obraRepository.findByIdAndHabilitadoTrue(1)).thenReturn(Optional.of(obra));
        when(obraRepository.save(any(Obra.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Obra result = obraService.cambiarEstado(1, EstadoObra.FINALIZADA);

        assertThat(result.getEstado()).isEqualTo(EstadoObra.FINALIZADA);
    }

    @Test
    void cambiarEstado_ToPendiente_ShouldSetPendiente() {
        obra.setEstado(EstadoObra.HABILITADA);
        when(obraRepository.findByIdAndHabilitadoTrue(1)).thenReturn(Optional.of(obra));
        when(obraRepository.save(any(Obra.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Obra result = obraService.cambiarEstado(1, EstadoObra.PENDIENTE);

        assertThat(result.getEstado()).isEqualTo(EstadoObra.PENDIENTE);
    }

    @Test
    void cambiarEstado_ToHabilitada_ShouldSetHabilitada() {
        obra.setEstado(EstadoObra.PENDIENTE);
        when(obraRepository.findByIdAndHabilitadoTrue(1)).thenReturn(Optional.of(obra));
        when(obraRepository.save(any(Obra.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Obra result = obraService.cambiarEstado(1, EstadoObra.HABILITADA);

        assertThat(result.getEstado()).isEqualTo(EstadoObra.HABILITADA);
    }

    @Test
    void cambiarEstado_SinCliente_ShouldThrow() {
        obra.setCliente(null);
        obra.setEstado(EstadoObra.PENDIENTE);
        when(obraRepository.findByIdAndHabilitadoTrue(1)).thenReturn(Optional.of(obra));

        assertThatThrownBy(() -> obraService.cambiarEstado(1, EstadoObra.FINALIZADA))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("La obra debe tener un cliente asignado");
    }

    @Test
    void cambiarEstado_ObraNoExistente_ShouldThrow() {
        when(obraRepository.findByIdAndHabilitadoTrue(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> obraService.cambiarEstado(99, EstadoObra.FINALIZADA))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Obra 99 no encontrada");
    }
}
