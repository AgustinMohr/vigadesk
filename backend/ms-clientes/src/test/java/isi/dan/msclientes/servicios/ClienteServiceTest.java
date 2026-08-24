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
import org.springframework.test.util.ReflectionTestUtils;

import isi.dan.msclientes.dao.ClienteRepository;
import isi.dan.msclientes.model.Cliente;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    @Mock
    private ClienteRepository clienteRepository;

    @InjectMocks
    private ClienteService clienteService;

    private Cliente cliente;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(clienteService, "maximoDescubiertoDefault", BigDecimal.valueOf(10000));
        cliente = new Cliente();
        cliente.setId(1);
        cliente.setNombre("Test Cliente");
        cliente.setCorreoElectronico("test@cliente.com");
        cliente.setCuit("12998887776");
        cliente.setMaximoDescubierto(BigDecimal.valueOf(100000));
        cliente.setMaxObrasActivas(5);
    }

    @Test
    void findAll_ShouldReturnAllClientes() {
        when(clienteRepository.findByHabilitadoTrue()).thenReturn(Collections.singletonList(cliente));

        List<Cliente> result = clienteService.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNombre()).isEqualTo("Test Cliente");
    }

    @Test
    void findById_WhenExists_ShouldReturnCliente() {
        when(clienteRepository.findByIdAndHabilitadoTrue(1)).thenReturn(Optional.of(cliente));

        Optional<Cliente> result = clienteService.findById(1);

        assertThat(result).isPresent();
        assertThat(result.get().getNombre()).isEqualTo("Test Cliente");
    }

    @Test
    void findById_WhenNotExists_ShouldReturnEmpty() {
        when(clienteRepository.findByIdAndHabilitadoTrue(99)).thenReturn(Optional.empty());

        Optional<Cliente> result = clienteService.findById(99);

        assertThat(result).isEmpty();
    }

    @Test
    void save_ShouldReturnSavedCliente() {
        when(clienteRepository.save(any(Cliente.class))).thenReturn(cliente);

        Cliente result = clienteService.save(cliente);

        assertThat(result).isNotNull();
        assertThat(result.getNombre()).isEqualTo("Test Cliente");
    }

    @Test
    void save_WhenMaximoDescubiertoNull_ShouldApplyExternalizedDefault() {
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        Cliente sinMaximo = new Cliente();
        sinMaximo.setNombre("Sin Maximo");
        sinMaximo.setCorreoElectronico("sinmaximo@cliente.com");
        sinMaximo.setMaxObrasActivas(2);

        Cliente result = clienteService.save(sinMaximo);

        assertThat(result.getMaximoDescubierto()).isEqualByComparingTo(BigDecimal.valueOf(10000));
    }

    @Test
    void update_ShouldUpdateOnlyNonNullFieldsAndSave() {
        when(clienteRepository.save(any(Cliente.class))).thenReturn(cliente);

        Cliente actualizado = new Cliente();
        actualizado.setNombre("Nombre Actualizado");
        actualizado.setCorreoElectronico("nuevo@cliente.com");

        Cliente result = clienteService.update(cliente, actualizado);

        assertThat(result.getNombre()).isEqualTo("Nombre Actualizado");
        assertThat(result.getCorreoElectronico()).isEqualTo("nuevo@cliente.com");
        assertThat(result.getCuit()).isEqualTo("12998887776");
        verify(clienteRepository).save(cliente);
    }

    @Test
    void update_ShouldUpdateAllProvidedFieldsIncludingMaxObrasActivas() {
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        Cliente nuevo = new Cliente();
        nuevo.setNombre("Nuevo Nombre");
        nuevo.setCorreoElectronico("nuevo@cliente.com");
        nuevo.setCuit("40998887776");
        nuevo.setMaximoDescubierto(BigDecimal.valueOf(999));
        nuevo.setMaxObrasActivas(4);

        Cliente result = clienteService.update(cliente, nuevo);

        assertThat(result.getNombre()).isEqualTo("Nuevo Nombre");
        assertThat(result.getCorreoElectronico()).isEqualTo("nuevo@cliente.com");
        assertThat(result.getCuit()).isEqualTo("40998887776");
        assertThat(result.getMaximoDescubierto()).isEqualByComparingTo(BigDecimal.valueOf(999));
        assertThat(result.getMaxObrasActivas()).isEqualTo(4);
    }

    @Test
    void deleteById_ShouldSoftDeleteCliente() {
        when(clienteRepository.findByIdAndHabilitadoTrue(1)).thenReturn(Optional.of(cliente));

        clienteService.deleteById(1);

        assertThat(cliente.isHabilitado()).isFalse();
        assertThat(cliente.getFechaBorrado()).isNotNull();
        verify(clienteRepository).save(cliente);
    }

    @Test
    void deleteById_WhenAlreadyDeleted_ShouldDoNothing() {
        when(clienteRepository.findByIdAndHabilitadoTrue(1)).thenReturn(Optional.empty());

        clienteService.deleteById(1);

        verify(clienteRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any(Cliente.class));
    }

    @Test
    void checkSaldo_WhenSaldoSuficiente_ShouldReturnTrue() {
        when(clienteRepository.findByIdAndHabilitadoTrue(1)).thenReturn(Optional.of(cliente));

        boolean result = clienteService.checkSaldo(1, BigDecimal.valueOf(50000));

        assertThat(result).isTrue();
    }

    @Test
    void checkSaldo_WhenSaldoInsuficiente_ShouldReturnFalse() {
        when(clienteRepository.findByIdAndHabilitadoTrue(1)).thenReturn(Optional.of(cliente));

        boolean result = clienteService.checkSaldo(1, BigDecimal.valueOf(150000));

        assertThat(result).isFalse();
    }

    @Test
    void checkSaldo_WhenSaldoExacto_ShouldReturnTrue() {
        when(clienteRepository.findByIdAndHabilitadoTrue(1)).thenReturn(Optional.of(cliente));

        boolean result = clienteService.checkSaldo(1, BigDecimal.valueOf(100000));

        assertThat(result).isTrue();
    }

    @Test
    void checkSaldo_WhenMontoNegativo_ShouldReturnTrue() {
        when(clienteRepository.findByIdAndHabilitadoTrue(1)).thenReturn(Optional.of(cliente));

        boolean result = clienteService.checkSaldo(1, BigDecimal.valueOf(-5000));

        assertThat(result).isTrue();
    }

    @Test
    void checkSaldo_WhenMontoCero_ShouldReturnTrue() {
        when(clienteRepository.findByIdAndHabilitadoTrue(1)).thenReturn(Optional.of(cliente));

        boolean result = clienteService.checkSaldo(1, BigDecimal.ZERO);

        assertThat(result).isTrue();
    }

    @Test
    void checkSaldo_WhenClienteNoExiste_ShouldThrowRuntimeException() {
        when(clienteRepository.findByIdAndHabilitadoTrue(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clienteService.checkSaldo(99, BigDecimal.valueOf(50000)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cliente no encontrado: 99");
    }

    @Test
    void getMaximoDescubierto_WhenExists_ShouldReturnMaximo() {
        when(clienteRepository.findByIdAndHabilitadoTrue(1)).thenReturn(Optional.of(cliente));

        BigDecimal result = clienteService.getMaximoDescubierto(1);

        assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(100000));
    }

    @Test
    void getMaximoDescubierto_WhenClienteNoExiste_ShouldThrowRuntimeException() {
        when(clienteRepository.findByIdAndHabilitadoTrue(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clienteService.getMaximoDescubierto(99))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cliente no encontrado: 99");
    }
}
