package isi.dan.ms.pedidos.servicio;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import isi.dan.ms.pedidos.modelo.Secuencia;

@Service
public class SecuenciaService {

    private static final String PEDIDO_SEQUENCE_ID = "pedido";
    private static final String REMITO_SEQUENCE_ID = "remito";

    @Autowired
    private MongoTemplate mongoTemplate;

    public Integer nextNumeroPedido() {
        return next(PEDIDO_SEQUENCE_ID);
    }

    public Integer nextNumeroRemito() {
        return next(REMITO_SEQUENCE_ID);
    }

    private Integer next(String sequenceId) {
        Query query = Query.query(Criteria.where("_id").is(sequenceId));
        Update update = new Update().inc("valor", 1);
        FindAndModifyOptions options = FindAndModifyOptions.options().upsert(true).returnNew(true);

        Secuencia secuencia = mongoTemplate.findAndModify(query, update, options, Secuencia.class);
        if (secuencia == null) {
            throw new IllegalStateException("No se pudo generar el correlativo " + sequenceId);
        }
        return Math.toIntExact(secuencia.getValor());
    }
}
