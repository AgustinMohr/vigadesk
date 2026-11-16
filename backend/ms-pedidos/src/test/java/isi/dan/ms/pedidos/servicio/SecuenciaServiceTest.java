package isi.dan.ms.pedidos.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import isi.dan.ms.pedidos.modelo.Secuencia;

@ExtendWith(MockitoExtension.class)
class SecuenciaServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private SecuenciaService secuenciaService;

    @Test
    void nextNumeroPedido_ShouldReturnIncrementedValue() {
        Secuencia secuencia = new Secuencia();
        secuencia.setValor(42);
        when(mongoTemplate.findAndModify(
                any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Secuencia.class)))
                .thenReturn(secuencia);

        Integer result = secuenciaService.nextNumeroPedido();

        assertThat(result).isEqualTo(42);
    }

    @Test
    void nextNumeroPedido_WhenMongoReturnsNull_ShouldThrow() {
        when(mongoTemplate.findAndModify(
                any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Secuencia.class)))
                .thenReturn(null);

        assertThatThrownBy(() -> secuenciaService.nextNumeroPedido())
                .isInstanceOf(IllegalStateException.class);
    }
}
