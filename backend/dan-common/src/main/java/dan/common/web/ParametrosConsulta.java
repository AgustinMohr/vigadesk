package dan.common.web;

/**
 * Validacion comun de los parametros de busqueda y paginacion de los listados (R2.11). Los valores invalidos
 * lanzan IllegalArgumentException, que el manejador de errores convierte en 400.
 */
public final class ParametrosConsulta {

    public static final int TEXTO_MAXIMO = 100;
    public static final int TAMANIO_MAXIMO = 100;
    public static final int TAMANIO_POR_DEFECTO = 100;

    /** Cabecera con el total de elementos que cumplen el filtro (sin paginar). */
    public static final String CABECERA_TOTAL = "X-Total-Count";

    private ParametrosConsulta() {
    }

    public static int pagina(Integer page) {
        if (page == null) {
            return 0;
        }
        if (page < 0) {
            throw new IllegalArgumentException("page debe ser mayor o igual a 0");
        }
        return page;
    }

    public static int tamanio(Integer size) {
        if (size == null) {
            return TAMANIO_POR_DEFECTO;
        }
        if (size < 1 || size > TAMANIO_MAXIMO) {
            throw new IllegalArgumentException("size debe estar entre 1 y " + TAMANIO_MAXIMO);
        }
        return size;
    }

    /** Devuelve el texto recortado, o null si es nulo o vacio. Falla si supera la longitud maxima. */
    public static String texto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String limpio = valor.trim();
        if (limpio.length() > TEXTO_MAXIMO) {
            throw new IllegalArgumentException(campo + " no puede superar los " + TEXTO_MAXIMO + " caracteres");
        }
        return limpio;
    }

    /** Escapa los comodines de LIKE (% _) y la barra de escape, para usar con escape '\'. */
    public static String escaparLike(String valor) {
        return valor.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
