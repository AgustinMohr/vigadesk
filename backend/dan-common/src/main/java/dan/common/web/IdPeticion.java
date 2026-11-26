package dan.common.web;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Identificador que acompana a una peticion por todos los servicios, para poder seguirla en los logs.
 * Viaja en el header {@value #HEADER} y aparece en cada linea JSON como {@value #CLAVE_MDC}.
 */
public final class IdPeticion {

    public static final String HEADER = "X-Request-Id";
    public static final String CLAVE_MDC = "requestId";

    /** Lo que llega de fuera se escribe en los logs: solo se acepta un valor corto y sin caracteres raros. */
    private static final Pattern VALIDO = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private IdPeticion() {
    }

    /** El identificador recibido si es aceptable; uno nuevo en caso contrario. */
    public static String aceptarOCrear(String recibido) {
        return recibido != null && VALIDO.matcher(recibido).matches() ? recibido : UUID.randomUUID().toString();
    }
}
