package dan.common.web;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Filtro de los microservicios: toma el {@code X-Request-Id} entrante (o crea uno), lo deja en el MDC mientras
 * dura la peticion, lo devuelve en la respuesta y escribe una linea por peticion con metodo, ruta, estado y
 * duracion. No registra la query string ni los headers, que pueden llevar datos personales o credenciales.
 * Debe registrarse antes que el resto de los filtros, para que los rechazos tambien queden identificados.
 */
public class RegistroPeticionFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(RegistroPeticionFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = IdPeticion.aceptarOCrear(request.getHeader(IdPeticion.HEADER));
        response.setHeader(IdPeticion.HEADER, id);
        long inicio = System.nanoTime();
        boolean termino = false;

        try (var mdc = MDC.putCloseable(IdPeticion.CLAVE_MDC, id)) {
            try {
                chain.doFilter(request, response);
                termino = true;
            } finally {
                // Si la cadena lanzo una excepcion, el contenedor respondera 500 despues de este filtro
                int estado = termino ? response.getStatus() : 500;
                LOG.info("{} {} {} {}ms", request.getMethod(), request.getRequestURI(), estado,
                        (System.nanoTime() - inicio) / 1_000_000);
            }
        }
    }
}
