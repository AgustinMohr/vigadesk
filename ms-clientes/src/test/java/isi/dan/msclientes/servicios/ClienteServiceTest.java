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
        when(clienteRepository.findAll()).thenReturn(Collections.singletonList(cliente));

        List<Cliente> result = clienteService.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNombre()).isEqualTo("Test Cliente");
    }

    @Test
    void findById_WhenExists_ShouldReturnCliente() {
        when(clienteRepository.findById(1)).thenReturn(Optional.of(cliente));

        Optional<Cliente> result = clienteService.findById(1);

        assertThat(result).isPresent();
        assertThat(result.get().getNombre()).isEqualTo("Test Cliente");
    }

    @Test
    void findById_WhenNotExists_ShouldReturnEmpty() {
        when(clienteRepository.findById(99)).thenReturn(Optional.empty());

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

	// TODO: arreglar test
    // @Test
    // void update_ShouldReturnUpdatedCliente() {
    //     when(clienteRepository.save(any(Cliente.class))).thenReturn(cliente);

    //     Cliente result = clienteService.update(cliente);

    //     assertThat(result).isNotNull();
    //     assertThat(result.getNombre()).isEqualTo("Test Cliente");
    // }

    @Test
    void deleteById_ShouldDeleteCliente() {
        clienteService.deleteById(1);

        verify(clienteRepository).deleteById(1);
    }

    @Test
    void checkSaldo_WhenSaldoSuficiente_ShouldReturnTrue() {
        when(clienteRepository.findById(1)).thenReturn(Optional.of(cliente));

        boolean result = clienteService.checkSaldo(1, BigDecimal.valueOf(50000));

        assertThat(result).isTrue();
    }

    @Test
    void checkSaldo_WhenSaldoInsuficiente_ShouldReturnFalse() {
        when(clienteRepository.findById(1)).thenReturn(Optional.of(cliente));

        boolean result = clienteService.checkSaldo(1, BigDecimal.valueOf(150000));

        assertThat(result).isFalse();
    }

    @Test
    void checkSaldo_WhenSaldoExacto_ShouldReturnTrue() {
        when(clienteRepository.findById(1)).thenReturn(Optional.of(cliente));

        boolean result = clienteService.checkSaldo(1, BigDecimal.valueOf(100000));

        assertThat(result).isTrue();
    }

    @Test
    void checkSaldo_WhenClienteNoExiste_ShouldThrowRuntimeException() {
        when(clienteRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clienteService.checkSaldo(99, BigDecimal.valueOf(50000)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cliente no encontrado: 99");
    }

    @Test
    void getMaximoDescubierto_WhenExists_ShouldReturnMaximo() {
        when(clienteRepository.findById(1)).thenReturn(Optional.of(cliente));

        BigDecimal result = clienteService.getMaximoDescubierto(1);

        assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(100000));
    }

    @Test
    void getMaximoDescubierto_WhenClienteNoExiste_ShouldThrowRuntimeException() {
        when(clienteRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clienteService.getMaximoDescubierto(99))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cliente no encontrado: 99");
    }
}
