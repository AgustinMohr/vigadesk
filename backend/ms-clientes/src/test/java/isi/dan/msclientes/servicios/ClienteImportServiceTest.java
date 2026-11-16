package isi.dan.msclientes.servicios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import isi.dan.msclientes.dao.ClienteRepository;
import isi.dan.msclientes.dto.ImportResult;
import isi.dan.msclientes.model.Cliente;

@ExtendWith(MockitoExtension.class)
class ClienteImportServiceTest {

    @Mock
    private ClienteRepository clienteRepository;

    @InjectMocks
    private ClienteImportService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "maximoDescubiertoDefault", BigDecimal.valueOf(10000));
    }

    private ByteArrayInputStream csv(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void importar_ShouldCreateClientWithCreditLimitAndInitialBalance() {
        when(clienteRepository.findByCorreoElectronico("nuevo@cliente.com")).thenReturn(Optional.empty());
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        ImportResult result = service.importar(csv(
                "nombre,correoElectronico,cuit,maximoDescubierto,saldoInicial\n"
                        + "Cliente Nuevo,nuevo@cliente.com,30-12345678-9,250000,15000.50\n"));

        ArgumentCaptor<Cliente> captor = ArgumentCaptor.forClass(Cliente.class);
        verify(clienteRepository).save(captor.capture());
        Cliente cliente = captor.getValue();
        assertThat(result.getCreados()).isEqualTo(1);
        assertThat(cliente.getMaximoDescubierto()).isEqualByComparingTo(BigDecimal.valueOf(250000));
        assertThat(cliente.getSaldoInicial()).isEqualByComparingTo(new BigDecimal("15000.50"));
    }

    @Test
    void importar_WhenEmailExists_ShouldUpdate() {
        Cliente existente = new Cliente();
        existente.setId(1);
        existente.setCorreoElectronico("nuevo@cliente.com");
        when(clienteRepository.findByCorreoElectronico("nuevo@cliente.com"))
                .thenReturn(Optional.of(existente));
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        ImportResult result = service.importar(csv(
                "nombre,correoElectronico,maximoDescubierto,saldoInicial\n"
                        + "Cliente Actualizado,nuevo@cliente.com,300000,0\n"));

        assertThat(result.getActualizados()).isEqualTo(1);
        assertThat(existente.getNombre()).isEqualTo("Cliente Actualizado");
        assertThat(existente.getMaximoDescubierto()).isEqualByComparingTo(BigDecimal.valueOf(300000));
    }

    @Test
    void importar_WhenNombreFaltante_ShouldThrow() {
        assertThatThrownBy(() -> service.importar(csv(
                "nombre,correoElectronico\n"
                        + ",sin-nombre@cliente.com\n")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nombre");
    }
}
