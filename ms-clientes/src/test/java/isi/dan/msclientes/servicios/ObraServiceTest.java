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
        cliente.setMaxObrasActivas(2);

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
    void save_WithClienteAndCupo_ShouldSetHabilitada() {
        when(clienteRepository.findById(1)).thenReturn(Optional.of(cliente));
        when(obraRepository.countByClienteIdAndEstado(1, EstadoObra.HABILITADA)).thenReturn(0L);
        when(obraRepository.save(any(Obra.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Obra result = obraService.save(obra);

        assertThat(result.getEstado()).isEqualTo(EstadoObra.HABILITADA);
    }

    @Test
    void save_WithClienteSinCupo_ShouldSetPendiente() {
        when(clienteRepository.findById(1)).thenReturn(Optional.of(cliente));
        when(obraRepository.countByClienteIdAndEstado(1, EstadoObra.HABILITADA)).thenReturn(2L);
        when(obraRepository.save(any(Obra.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Obra result = obraService.save(obra);

        assertThat(result.getEstado()).isEqualTo(EstadoObra.PENDIENTE);
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
    void asignarCliente_WhenBothExistAndCupo_ShouldSetHabilitada() {
        when(obraRepository.findById(1)).thenReturn(Optional.of(obra));
        when(clienteRepository.findById(1)).thenReturn(Optional.of(cliente));
        when(obraRepository.countByClienteIdAndEstado(1, EstadoObra.HABILITADA)).thenReturn(0L);
        when(obraRepository.save(any(Obra.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Obra result = obraService.asignarCliente(1, 1);

        assertThat(result).isNotNull();
        assertThat(result.getCliente().getId()).isEqualTo(1);
        assertThat(result.getEstado()).isEqualTo(EstadoObra.HABILITADA);
    }

    @Test
    void asignarCliente_WhenBothExistSinCupo_ShouldSetPendiente() {
        when(obraRepository.findById(1)).thenReturn(Optional.of(obra));
        when(clienteRepository.findById(1)).thenReturn(Optional.of(cliente));
        when(obraRepository.countByClienteIdAndEstado(1, EstadoObra.HABILITADA)).thenReturn(2L);
        when(obraRepository.save(any(Obra.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Obra result = obraService.asignarCliente(1, 1);

        assertThat(result).isNotNull();
        assertThat(result.getEstado()).isEqualTo(EstadoObra.PENDIENTE);
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

    @Test
    void cambiarEstado_ToFinalizada_ShouldSetFinalizadaAndHabilitarPendiente() {
        obra.setEstado(EstadoObra.HABILITADA);
        Obra pendiente = new Obra();
        pendiente.setId(2);
        pendiente.setDireccion("Obra Pendiente");
        pendiente.setCliente(cliente);
        pendiente.setEstado(EstadoObra.PENDIENTE);

        when(obraRepository.findById(1)).thenReturn(Optional.of(obra));
        when(clienteRepository.findById(1)).thenReturn(Optional.of(cliente));
        when(obraRepository.countByClienteIdAndEstado(1, EstadoObra.HABILITADA)).thenReturn(0L);
        when(obraRepository.findByClienteIdAndEstadoOrderByIdAsc(1, EstadoObra.PENDIENTE))
                .thenReturn(Collections.singletonList(pendiente));
        when(obraRepository.save(any(Obra.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Obra result = obraService.cambiarEstado(1, EstadoObra.FINALIZADA);

        assertThat(result.getEstado()).isEqualTo(EstadoObra.FINALIZADA);
        verify(obraRepository).save(pendiente);
        assertThat(pendiente.getEstado()).isEqualTo(EstadoObra.HABILITADA);
    }

    @Test
    void cambiarEstado_ToFinalizadaSinPendientes_ShouldSetFinalizada() {
        obra.setEstado(EstadoObra.HABILITADA);

        when(obraRepository.findById(1)).thenReturn(Optional.of(obra));
        when(clienteRepository.findById(1)).thenReturn(Optional.of(cliente));
        when(obraRepository.countByClienteIdAndEstado(1, EstadoObra.HABILITADA)).thenReturn(0L);
        when(obraRepository.findByClienteIdAndEstadoOrderByIdAsc(1, EstadoObra.PENDIENTE))
                .thenReturn(Collections.emptyList());
        when(obraRepository.save(any(Obra.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Obra result = obraService.cambiarEstado(1, EstadoObra.FINALIZADA);

        assertThat(result.getEstado()).isEqualTo(EstadoObra.FINALIZADA);
    }

    @Test
    void cambiarEstado_ToPendiente_ShouldSetPendiente() {
        obra.setEstado(EstadoObra.HABILITADA);

        when(obraRepository.findById(1)).thenReturn(Optional.of(obra));
        when(obraRepository.save(any(Obra.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Obra result = obraService.cambiarEstado(1, EstadoObra.PENDIENTE);

        assertThat(result.getEstado()).isEqualTo(EstadoObra.PENDIENTE);
    }

    @Test
    void cambiarEstado_ToHabilitadaConCupo_ShouldSetHabilitada() {
        obra.setEstado(EstadoObra.PENDIENTE);

        when(obraRepository.findById(1)).thenReturn(Optional.of(obra));
        when(clienteRepository.findById(1)).thenReturn(Optional.of(cliente));
        when(obraRepository.countByClienteIdAndEstado(1, EstadoObra.HABILITADA)).thenReturn(0L);
        when(obraRepository.save(any(Obra.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Obra result = obraService.cambiarEstado(1, EstadoObra.HABILITADA);

        assertThat(result.getEstado()).isEqualTo(EstadoObra.HABILITADA);
    }

    @Test
    void cambiarEstado_ToHabilitadaSinCupo_ShouldThrow() {
        obra.setEstado(EstadoObra.PENDIENTE);

        when(obraRepository.findById(1)).thenReturn(Optional.of(obra));
        when(clienteRepository.findById(1)).thenReturn(Optional.of(cliente));
        when(obraRepository.countByClienteIdAndEstado(1, EstadoObra.HABILITADA)).thenReturn(2L);

        assertThatThrownBy(() -> obraService.cambiarEstado(1, EstadoObra.HABILITADA))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No se puede habilitar");
    }

    @Test
    void cambiarEstado_SinCliente_ShouldThrow() {
        obra.setCliente(null);
        obra.setEstado(EstadoObra.PENDIENTE);

        when(obraRepository.findById(1)).thenReturn(Optional.of(obra));

        assertThatThrownBy(() -> obraService.cambiarEstado(1, EstadoObra.FINALIZADA))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("La obra debe tener un cliente asignado");
    }

    @Test
    void save_WithClienteInexistente_ShouldThrow() {
        obra.setEstado(null);
        when(clienteRepository.findById(99)).thenReturn(Optional.empty());

        obra.setCliente(new Cliente());
        obra.getCliente().setId(99);

        assertThatThrownBy(() -> obraService.save(obra))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cliente no encontrado: 99");
    }

    @Test
    void asignarCliente_ConCupoDisponible_ShouldSetHabilitada() {
        cliente.setMaxObrasActivas(5);

        when(obraRepository.findById(1)).thenReturn(Optional.of(obra));
        when(clienteRepository.findById(1)).thenReturn(Optional.of(cliente));
        when(obraRepository.countByClienteIdAndEstado(1, EstadoObra.HABILITADA)).thenReturn(0L);
        when(obraRepository.save(any(Obra.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Obra result = obraService.asignarCliente(1, 1);

        assertThat(result.getEstado()).isEqualTo(EstadoObra.HABILITADA);
    }

    @Test
    void asignarCliente_ObraYaAsignada_ShouldReevaluate() {
        Cliente cliente2 = new Cliente();
        cliente2.setId(2);
        cliente2.setNombre("Otro Cliente");
        cliente2.setMaxObrasActivas(2);
        obra.setCliente(cliente2);
        obra.setEstado(EstadoObra.PENDIENTE);

        when(obraRepository.findById(1)).thenReturn(Optional.of(obra));
        when(clienteRepository.findById(1)).thenReturn(Optional.of(cliente));
        when(obraRepository.countByClienteIdAndEstado(1, EstadoObra.HABILITADA)).thenReturn(0L);
        when(obraRepository.save(any(Obra.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Obra result = obraService.asignarCliente(1, 1);

        assertThat(result.getCliente().getId()).isEqualTo(1);
        assertThat(result.getEstado()).isEqualTo(EstadoObra.HABILITADA);
    }

    @Test
    void cambiarEstado_ToFinalizada_SinPendientes_ShouldSetFinalizada() {
        cliente.setMaxObrasActivas(3);
        obra.setEstado(EstadoObra.HABILITADA);

        when(obraRepository.findById(1)).thenReturn(Optional.of(obra));
        when(clienteRepository.findById(1)).thenReturn(Optional.of(cliente));
        when(obraRepository.countByClienteIdAndEstado(1, EstadoObra.HABILITADA)).thenReturn(1L);
        when(obraRepository.findByClienteIdAndEstadoOrderByIdAsc(1, EstadoObra.PENDIENTE))
                .thenReturn(Collections.emptyList());
        when(obraRepository.save(any(Obra.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Obra result = obraService.cambiarEstado(1, EstadoObra.FINALIZADA);

        assertThat(result.getEstado()).isEqualTo(EstadoObra.FINALIZADA);
    }

    @Test
    void cambiarEstado_ToFinalizada_ConMultiplesPendientes_ShouldHabilitarSoloPrimero() {
        obra.setEstado(EstadoObra.HABILITADA);
        Obra pendiente1 = new Obra();
        pendiente1.setId(2);
        pendiente1.setDireccion("Pendiente 1");
        pendiente1.setCliente(cliente);
        pendiente1.setEstado(EstadoObra.PENDIENTE);
        Obra pendiente2 = new Obra();
        pendiente2.setId(3);
        pendiente2.setDireccion("Pendiente 2");
        pendiente2.setCliente(cliente);
        pendiente2.setEstado(EstadoObra.PENDIENTE);

        when(obraRepository.findById(1)).thenReturn(Optional.of(obra));
        when(clienteRepository.findById(1)).thenReturn(Optional.of(cliente));
        when(obraRepository.countByClienteIdAndEstado(1, EstadoObra.HABILITADA)).thenReturn(0L);
        when(obraRepository.findByClienteIdAndEstadoOrderByIdAsc(1, EstadoObra.PENDIENTE))
                .thenReturn(List.of(pendiente1, pendiente2));
        when(obraRepository.save(any(Obra.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Obra result = obraService.cambiarEstado(1, EstadoObra.FINALIZADA);

        assertThat(result.getEstado()).isEqualTo(EstadoObra.FINALIZADA);
        assertThat(pendiente1.getEstado()).isEqualTo(EstadoObra.HABILITADA);
        assertThat(pendiente2.getEstado()).isEqualTo(EstadoObra.PENDIENTE);
    }

    @Test
    void cambiarEstado_ToHabilitada_ConCupoDisponible_ShouldSetHabilitada() {
        cliente.setMaxObrasActivas(3);
        obra.setEstado(EstadoObra.PENDIENTE);

        when(obraRepository.findById(1)).thenReturn(Optional.of(obra));
        when(clienteRepository.findById(1)).thenReturn(Optional.of(cliente));
        when(obraRepository.countByClienteIdAndEstado(1, EstadoObra.HABILITADA)).thenReturn(0L);
        when(obraRepository.save(any(Obra.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Obra result = obraService.cambiarEstado(1, EstadoObra.HABILITADA);

        assertThat(result.getEstado()).isEqualTo(EstadoObra.HABILITADA);
    }

    @Test
    void cambiarEstado_ToHabilitada_DesdeFinalizada_ShouldWork() {
        obra.setEstado(EstadoObra.FINALIZADA);

        when(obraRepository.findById(1)).thenReturn(Optional.of(obra));
        when(clienteRepository.findById(1)).thenReturn(Optional.of(cliente));
        when(obraRepository.countByClienteIdAndEstado(1, EstadoObra.HABILITADA)).thenReturn(0L);
        when(obraRepository.save(any(Obra.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Obra result = obraService.cambiarEstado(1, EstadoObra.HABILITADA);

        assertThat(result.getEstado()).isEqualTo(EstadoObra.HABILITADA);
    }

    @Test
    void cambiarEstado_ToPendiente_DesdeFinalizada_ShouldWork() {
        obra.setEstado(EstadoObra.FINALIZADA);

        when(obraRepository.findById(1)).thenReturn(Optional.of(obra));
        when(obraRepository.save(any(Obra.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Obra result = obraService.cambiarEstado(1, EstadoObra.PENDIENTE);

        assertThat(result.getEstado()).isEqualTo(EstadoObra.PENDIENTE);
    }

    @Test
    void cambiarEstado_ToFinalizada_ConPendienteEnBorde_ShouldHabilitar() {
        cliente.setMaxObrasActivas(3);
        obra.setEstado(EstadoObra.HABILITADA);
        Obra pendiente = new Obra();
        pendiente.setId(2);
        pendiente.setDireccion("Pendiente en borde");
        pendiente.setCliente(cliente);
        pendiente.setEstado(EstadoObra.PENDIENTE);

        when(obraRepository.findById(1)).thenReturn(Optional.of(obra));
        when(clienteRepository.findById(1)).thenReturn(Optional.of(cliente));
        when(obraRepository.countByClienteIdAndEstado(1, EstadoObra.HABILITADA)).thenReturn(2L);
        when(obraRepository.findByClienteIdAndEstadoOrderByIdAsc(1, EstadoObra.PENDIENTE))
                .thenReturn(Collections.singletonList(pendiente));
        when(obraRepository.save(any(Obra.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Obra result = obraService.cambiarEstado(1, EstadoObra.FINALIZADA);

        assertThat(result.getEstado()).isEqualTo(EstadoObra.FINALIZADA);
        assertThat(pendiente.getEstado()).isEqualTo(EstadoObra.HABILITADA);
    }

    @Test
    void cambiarEstado_ObraNoExistente_ShouldThrow() {
        when(obraRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> obraService.cambiarEstado(99, EstadoObra.FINALIZADA))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Obra no encontrada: 99");
    }

    @Test
    void cambiarEstado_MaxCero_ShouldThrow() {
        cliente.setMaxObrasActivas(0);
        obra.setEstado(EstadoObra.PENDIENTE);

        when(obraRepository.findById(1)).thenReturn(Optional.of(obra));
        when(clienteRepository.findById(1)).thenReturn(Optional.of(cliente));
        when(obraRepository.countByClienteIdAndEstado(1, EstadoObra.HABILITADA)).thenReturn(0L);

        assertThatThrownBy(() -> obraService.cambiarEstado(1, EstadoObra.HABILITADA))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No se puede habilitar");
    }
}
