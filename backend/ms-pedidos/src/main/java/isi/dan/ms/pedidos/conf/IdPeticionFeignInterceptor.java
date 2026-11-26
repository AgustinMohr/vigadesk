package isi.dan.ms.pedidos.conf;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import dan.common.web.IdPeticion;
import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * Agrega a las llamadas Feign el identificador de la peticion en curso, para seguirla en ms-clientes y
 * ms-productos. Es un header adicional: no cambia rutas ni cuerpos. Fuera de una peticion HTTP no hace nada.
 */
@Component
public class IdPeticionFeignInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String id = MDC.get(IdPeticion.CLAVE_MDC);
        if (id != null) {
            template.header(IdPeticion.HEADER, id);
        }
    }
}
