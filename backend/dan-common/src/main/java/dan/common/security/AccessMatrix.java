package dan.common.security;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Unica definicion de quien puede hacer que. La usan el gateway y los tres microservicios,
 * asi una regla se cambia en un solo lugar.
 *
 * <p>Las reglas se evaluan en orden sobre el path canonico (ver {@link PathNormalizer}) y sin el
 * prefijo de servicio del gateway (ver {@link #sinPrefijoDeServicio}). La primera que coincide
 * decide; si ninguna coincide, se deniega.
 *
 * <p>Los roles son {@code ADMIN}, {@code GUEST} y cualquier otro valor se trata como {@code CLIENTE}.
 *
 * <table>
 *   <caption>Matriz de acceso</caption>
 *   <tr><th>#</th><th>Rol</th><th>Metodo</th><th>Ruta</th><th>Decision</th></tr>
 *   <tr><td>1</td><td>ADMIN</td><td>cualquiera</td><td>cualquiera</td><td>permite</td></tr>
 *   <tr><td>2</td><td>no ADMIN</td><td>cualquiera</td><td>/actuator/**</td><td>deniega</td></tr>
 *   <tr><td>3</td><td>no ADMIN</td><td>cualquiera</td><td>/api/usuarios/**</td><td>deniega</td></tr>
 *   <tr><td>4</td><td>GUEST</td><td>GET, HEAD, OPTIONS</td><td>/api/productos/**, /api/categorias/**</td><td>permite</td></tr>
 *   <tr><td>5</td><td>GUEST</td><td>cualquiera</td><td>cualquiera</td><td>deniega</td></tr>
 *   <tr><td>6</td><td>CLIENTE</td><td>GET, HEAD, OPTIONS</td><td>cualquiera</td><td>permite</td></tr>
 *   <tr><td>7</td><td>CLIENTE</td><td>POST</td><td>/api/pedidos, /api/pedidos/</td><td>permite</td></tr>
 *   <tr><td>8</td><td>CLIENTE</td><td>cualquiera</td><td>cualquiera</td><td>deniega</td></tr>
 * </table>
 */
public final class AccessMatrix {

    public static final String ADMIN = "ADMIN";
    public static final String GUEST = "GUEST";

    /** Prefijos con los que el gateway enruta a cada servicio; llegan al servicio sin el prefijo (RewritePath). */
    private static final List<String> SERVICE_PREFIXES = List.of("/clientes", "/productos", "/pedidos");

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    /** Rutas que el servicio atiende sin rol (trafico servicio a servicio y chequeos de vida). */
    private static final Pattern FEIGN_CLIENTES_READ =
            Pattern.compile("^/api/clientes/\\d+/(saldo|maximo-descubierto|contexto-pedido)$");
    private static final Set<String> FEIGN_PRODUCTOS_STOCK = Set.of(
            "/api/productos/stock/check",
            "/api/productos/stock/reserve",
            "/api/productos/stock/release");

    private AccessMatrix() {
    }

    /**
     * @param rol          valor de {@code X-User-Rol} o del claim {@code rol}
     * @param method       metodo HTTP
     * @param canonicalPath path canonico, sin prefijo de servicio
     */
    public static boolean isAuthorized(String rol, String method, String canonicalPath) {
        if (ADMIN.equalsIgnoreCase(rol)) {
            return true;
        }
        if (matchesPrefix(canonicalPath, "/actuator") || matchesPrefix(canonicalPath, "/api/usuarios")) {
            return false;
        }
        boolean safe = isSafeMethod(method);
        if (GUEST.equalsIgnoreCase(rol)) {
            return safe && (matchesPrefix(canonicalPath, "/api/productos")
                    || matchesPrefix(canonicalPath, "/api/categorias"));
        }
        if (safe) {
            return true;
        }
        return "POST".equalsIgnoreCase(method)
                && (canonicalPath.equals("/api/pedidos") || canonicalPath.equals("/api/pedidos/"));
    }

    public static boolean isSafeMethod(String method) {
        return method != null && SAFE_METHODS.contains(method.toUpperCase(Locale.ROOT));
    }

    /**
     * Quita el prefijo de servicio del gateway ({@code /clientes}, {@code /productos}, {@code /pedidos}),
     * para aplicar a esas rutas las mismas reglas que al path que ve el servicio.
     */
    public static String sinPrefijoDeServicio(String path) {
        for (String prefix : SERVICE_PREFIXES) {
            if (path.equals(prefix)) {
                return "/";
            }
            if (path.startsWith(prefix + "/")) {
                return path.substring(prefix.length());
            }
        }
        return path;
    }

    /** Rutas que el gateway deja pasar sin token: vida del gateway y login (directo o con prefijo de servicio). */
    public static boolean isPublicAtGateway(String path) {
        return path.equals("/actuator/health")
                || path.equals("/api/usuarios/login")
                || path.equals("/clientes/api/usuarios/login");
    }

    /**
     * Rutas que un servicio atiende sin pasar por el filtro de roles. Se evalua sobre el path crudo,
     * a proposito: una variante codificada de una ruta interna no queda exenta.
     */
    public static boolean skipsRoleFilter(String method, String rawPath) {
        if ("OPTIONS".equals(method)) {
            return true;
        }
        if (rawPath.equals("/actuator/health") || rawPath.startsWith("/actuator/health/")) {
            return true;
        }
        if (rawPath.equals("/api/usuarios/login")) {
            return true;
        }
        if ("GET".equals(method) && FEIGN_CLIENTES_READ.matcher(rawPath).matches()) {
            return true;
        }
        return "PUT".equals(method) && FEIGN_PRODUCTOS_STOCK.contains(rawPath);
    }

    private static boolean matchesPrefix(String path, String prefix) {
        // startsWith a proposito (no solo el segmento): /actuatorx o /api/usuariosx tambien se tratan como el prefijo
        return path.startsWith(prefix);
    }
}
