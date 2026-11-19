package isi.dan.ms.pedidos.servicio;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import isi.dan.ms.pedidos.exception.ExternalServiceUnavailableException;
import isi.dan.ms.pedidos.modelo.BloqueoCliente;
import isi.dan.ms.pedidos.modelo.EstadoPedido;
import isi.dan.ms.pedidos.modelo.Pedido;

/**
 * Credito del cliente: consumo por agregacion y bloqueo por cliente, de modo que
 * dos pedidos simultaneos no lean el mismo consumo y superen el maximo.
 */
@Service
public class CreditoService {

    static final Duration VIGENCIA_BLOQUEO = Duration.ofSeconds(30);
    static final Duration ESPERA_MAXIMA = Duration.ofSeconds(15);
    private static final long PAUSA_MS = 20;

    @Autowired
    private MongoTemplate mongoTemplate;

    /**
     * Suma el total de los pedidos activos del cliente en los estados dados, sin cargar documentos.
     * El total puede estar guardado como texto (representacion por defecto de BigDecimal) o como
     * Decimal128; $toDecimal acepta ambos.
     */
    public BigDecimal consumo(Integer clienteId, List<EstadoPedido> estados) {
        Criteria filtro = Criteria.where("cliente._id").is(clienteId)
                .and("estado").in(estados)
                .and("habilitado").ne(false);
        AggregationOperation suma = context -> new Document("$group", new Document("_id", null)
                .append("consumo", new Document("$sum", new Document("$toDecimal", "$total"))));

        Document resultado = mongoTemplate
                .aggregate(Aggregation.newAggregation(Aggregation.match(filtro), suma), Pedido.class, Document.class)
                .getUniqueMappedResult();
        if (resultado == null) {
            return BigDecimal.ZERO;
        }
        Object valor = resultado.get("consumo");
        if (valor instanceof Decimal128 decimal) {
            return decimal.bigDecimalValue();
        }
        return valor == null ? BigDecimal.ZERO : new BigDecimal(valor.toString());
    }

    /** Ejecuta la accion con el bloqueo del cliente; lo libera siempre al terminar. */
    public <T> T conBloqueo(Integer clienteId, Supplier<T> accion) {
        String token = adquirir(clienteId);
        try {
            return accion.get();
        } finally {
            liberar(clienteId, token);
        }
    }

    private String adquirir(Integer clienteId) {
        String id = "cliente:" + clienteId;
        String token = UUID.randomUUID().toString();
        Instant limite = Instant.now().plus(ESPERA_MAXIMA);
        while (true) {
            Instant ahora = Instant.now();
            // Toma el bloqueo si no existe o si vencio. Si esta vigente, el upsert choca con _id duplicado.
            Query libre = Query.query(Criteria.where("_id").is(id).and("expiraEn").lt(ahora));
            Update tomar = new Update().set("token", token).set("expiraEn", ahora.plus(VIGENCIA_BLOQUEO));
            try {
                mongoTemplate.findAndModify(libre, tomar,
                        FindAndModifyOptions.options().upsert(true).returnNew(true), BloqueoCliente.class);
                return token;
            } catch (DuplicateKeyException e) {
                if (ahora.isAfter(limite)) {
                    throw new ExternalServiceUnavailableException(
                            "No se pudo obtener el bloqueo de credito del cliente " + clienteId, e);
                }
                pausar();
            }
        }
    }

    private void liberar(Integer clienteId, String token) {
        Query propio = Query.query(Criteria.where("_id").is("cliente:" + clienteId).and("token").is(token));
        mongoTemplate.updateFirst(propio, new Update().set("expiraEn", Instant.EPOCH), BloqueoCliente.class);
    }

    private void pausar() {
        try {
            Thread.sleep(PAUSA_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceUnavailableException("Interrumpido esperando el bloqueo de credito", e);
        }
    }
}
