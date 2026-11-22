package dan.common.security;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Convierte el path crudo de una peticion en su forma canonica antes de autorizar.
 *
 * El enrutamiento y los controladores comparan el path decodificado, asi que una
 * regla que mira el texto crudo se puede evitar con una variante equivalente
 * (por ejemplo {@code /api/%75suarios}, {@code //api/usuarios} o {@code /api/./usuarios}).
 * La decision de autorizacion se toma siempre sobre el resultado de {@link #normalize}.
 *
 * Reglas:
 * <ul>
 *   <li>Decodifica el percent-encoding una sola vez (UTF-8).</li>
 *   <li>Colapsa {@code //} y resuelve {@code .} y {@code ..}.</li>
 *   <li>Conserva la barra final.</li>
 *   <li>Rechaza (devuelve {@code null}) lo ambiguo: {@code %2f}, {@code %5c}, {@code %00},
 *       {@code %3b}, {@code \}, {@code ;}, caracteres de control, doble codificacion,
 *       secuencias invalidas y {@code ..} que sale de la raiz.</li>
 * </ul>
 */
public final class PathNormalizer {

    private static final Pattern PERCENT_ESCAPE = Pattern.compile("%[0-9a-fA-F]{2}");

    private PathNormalizer() {
    }

    /**
     * @return el path canonico, o {@code null} si el path no es seguro de interpretar.
     */
    public static String normalize(String rawPath) {
        if (rawPath == null || rawPath.isEmpty() || rawPath.charAt(0) != '/') {
            return null;
        }
        if (rawPath.indexOf('\\') >= 0 || rawPath.indexOf(';') >= 0 || rawPath.indexOf('\0') >= 0) {
            return null;
        }
        String lower = rawPath.toLowerCase(Locale.ROOT);
        if (lower.contains("%2f") || lower.contains("%5c") || lower.contains("%00") || lower.contains("%3b")) {
            return null;
        }

        String decoded;
        try {
            // El '+' es un caracter literal en un path: se protege antes de decodificar.
            decoded = URLDecoder.decode(rawPath.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (PERCENT_ESCAPE.matcher(decoded).find()) {
            return null;
        }
        for (int i = 0; i < decoded.length(); i++) {
            char c = decoded.charAt(i);
            if (c < 0x20 || c == 0x7f) {
                return null;
            }
        }

        Deque<String> segments = new ArrayDeque<>();
        for (String segment : decoded.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                if (segments.isEmpty()) {
                    return null;
                }
                segments.removeLast();
                continue;
            }
            segments.addLast(segment);
        }

        if (segments.isEmpty()) {
            return "/";
        }
        String joined = "/" + String.join("/", segments);
        return decoded.endsWith("/") ? joined + "/" : joined;
    }
}
