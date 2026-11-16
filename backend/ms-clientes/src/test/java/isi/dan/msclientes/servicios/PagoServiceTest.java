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
import isi.dan.msclientes.dao.PagoRepository;
import isi.dan.msclientes.exception.ClienteNotFoundException;
import isi.dan.msclientes.model.Cliente;
import isi.dan.msclientes.model.Pago;

@ExtendWith(MockitoExtension.class)
class PagoServiceTest {

    @Mock
    private PagoRepository pagoRepository;

    @Mock
    private ClienteRepository clienteRepository;

    @InjectMocks
    private PagoService pagoService;

    private Cliente cliente;
    private Pago pago;

    @BeforeEach
    void setUp() {
        cliente = new Cliente();
        cliente.setId(1);
        cliente.setNombre("Test Cliente");

        pago = new Pago();
        pago.setMonto(BigDecimal.valueOf(5000));
        pago.setMetodoPago("TRANSFERENCIA");
    }

    @Test
    void registrar_ShouldAssociateClienteAndSetFechaAndActor() throws ClienteNotFoundException {
        when(clienteRepository.findByIdAndHabilitadoTrue(1)).thenReturn(Optional.of(cliente));
        when(pagoRepository.save(any(Pago.class))).thenAnswer(i -> i.getArgument(0));

        Pago result = pagoService.registrar(1, pago, "Cajero");

        assertThat(result.getCliente().getId()).isEqualTo(1);
        assertThat(result.getFecha()).isNotNull();
        assertThat(result.getRegistradoPor()).isEqualTo("Cajero");
        assertThat(result.isHabilitado()).isTrue();
        assertThat(result.getMonto()).isEqualByComparingTo(BigDecimal.valueOf(5000));
    }

    @Test
    void registrar_WhenMontoInvalid_ShouldThrow() {
        pago.setMonto(BigDecimal.ZERO);

        assertThatThrownBy(() -> pagoService.registrar(1, pago))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("monto");
    }

    @Test
    void registrar_WhenMetodoPagoBlank_ShouldThrow() {
        pago.setMetodoPago(" ");

        assertThatThrownBy(() -> pagoService.registrar(1, pago))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("metodo de pago");
    }

    @Test
    void registrar_WhenClienteDoesNotExist_ShouldThrow() {
        when(clienteRepository.findByIdAndHabilitadoTrue(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pagoService.registrar(99, pago))
                .isInstanceOf(ClienteNotFoundException.class);
    }

    @Test
    void totalPagos_ShouldSumActivePayments() {
        Pago otro = new Pago();
        otro.setMonto(BigDecimal.valueOf(2500));
        when(pagoRepository.findByClienteIdAndHabilitadoTrueOrderByFechaDesc(1))
                .thenReturn(List.of(pago, otro));

        BigDecimal result = pagoService.totalPagos(1);

        assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(7500));
    }

    @Test
    void findByCliente_ShouldReturnPayments() {
        when(pagoRepository.findByClienteIdAndHabilitadoTrueOrderByFechaDesc(1))
                .thenReturn(Collections.singletonList(pago));

        List<Pago> result = pagoService.findByCliente(1);

        assertThat(result).hasSize(1);
    }

    @Test
    void delete_ShouldSoftDeletePayment() {
        pago.setId(10);
        pago.setCliente(cliente);
        when(pagoRepository.findByIdAndHabilitadoTrue(10)).thenReturn(Optional.of(pago));

        pagoService.delete(1, 10);

        assertThat(pago.isHabilitado()).isFalse();
        assertThat(pago.getFechaBorrado()).isNotNull();
        verify(pagoRepository).save(pago);
    }

    @Test
    void delete_WhenPaymentBelongsToAnotherClient_ShouldThrow() {
        Cliente otro = new Cliente();
        otro.setId(2);
        pago.setId(10);
        pago.setCliente(otro);
        when(pagoRepository.findByIdAndHabilitadoTrue(10)).thenReturn(Optional.of(pago));

        assertThatThrownBy(() -> pagoService.delete(1, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
