package isi.dan.ms.pedidos.conf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import isi.dan.ms.pedidos.modelo.Pedido;

/**
 * Las bases creadas antes de R2.6 tienen un indice unico global sobre idempotencyKey. Ese indice
 * impide que dos clientes usen la misma clave, asi que se elimina; lo reemplaza el indice
 * compuesto por cliente que crea Spring Data a partir de {@link Pedido}.
 */
@Component
@ConditionalOnProperty(name = "pedidos.limpiar-indices-anteriores", havingValue = "true", matchIfMissing = true)
public class LimpiezaIndicesMongo implements ApplicationRunner {

    static final String INDICE_GLOBAL_ANTERIOR = "idempotencyKey";

    private static final Logger log = LoggerFactory.getLogger(LimpiezaIndicesMongo.class);

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        eliminarIndiceGlobalAnterior();
    }

    public void eliminarIndiceGlobalAnterior() {
        boolean existe = mongoTemplate.indexOps(Pedido.class).getIndexInfo().stream()
                .anyMatch(i -> INDICE_GLOBAL_ANTERIOR.equals(i.getName()));
        if (existe) {
            mongoTemplate.indexOps(Pedido.class).dropIndex(INDICE_GLOBAL_ANTERIOR);
            log.info("Eliminado el indice unico global anterior de idempotencyKey");
        }
    }
}
