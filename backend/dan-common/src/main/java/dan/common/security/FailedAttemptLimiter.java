package dan.common.security;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Limitador de intentos fallidos en memoria, por clave (usuario, IP...). Una clave queda bloqueada cuando
 * acumula {@code maxFailures} fallos dentro de la ventana; el bloqueo dura hasta que el primer fallo sale
 * de la ventana. Un exito de la clave la reinicia.
 *
 * <p>Esta en memoria a proposito: no agrega infraestructura (Redis) solo para esto. Con varias
 * instancias del servicio el limite es por instancia. La cantidad de claves esta acotada para que un
 * atacante no pueda agotar la memoria con claves distintas.
 */
public class FailedAttemptLimiter {

    private static final int MAX_KEYS = 50_000;

    private final int maxFailures;
    private final long windowMillis;
    private final LongSupplier clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private volatile long lastEviction;

    public FailedAttemptLimiter(int maxFailures, Duration window) {
        this(maxFailures, window, System::currentTimeMillis);
    }

    FailedAttemptLimiter(int maxFailures, Duration window, LongSupplier clock) {
        if (maxFailures < 1 || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("maxFailures y window deben ser positivos");
        }
        this.maxFailures = maxFailures;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    /** @return true si la clave ya acumulo el maximo de fallos dentro de la ventana. */
    public boolean isBlocked(String key) {
        return retryAfterSeconds(key) > 0;
    }

    /** @return segundos hasta que la clave deje de estar bloqueada; 0 si no lo esta. */
    public long retryAfterSeconds(String key) {
        Window w = windows.get(key);
        if (w == null) {
            return 0;
        }
        long now = clock.getAsLong();
        synchronized (w) {
            w.expire(now, windowMillis);
            if (w.size() < maxFailures) {
                return 0;
            }
            long libera = w.oldest() + windowMillis - now;
            return Math.max(1, (libera + 999) / 1000);
        }
    }

    public void recordFailure(String key) {
        long now = clock.getAsLong();
        if (windows.size() >= MAX_KEYS && now - lastEviction >= 1000) {
            lastEviction = now;
            evictExpired(now);
        }
        if (windows.size() >= MAX_KEYS && !windows.containsKey(key)) {
            return; // sin lugar: no se registra la clave nueva antes que crecer sin limite
        }
        Window w = windows.computeIfAbsent(key, k -> new Window());
        synchronized (w) {
            w.expire(now, windowMillis);
            w.add(now);
        }
    }

    public void reset(String key) {
        windows.remove(key);
    }

    int trackedKeys() {
        return windows.size();
    }

    private void evictExpired(long now) {
        Iterator<Map.Entry<String, Window>> it = windows.entrySet().iterator();
        while (it.hasNext()) {
            Window w = it.next().getValue();
            synchronized (w) {
                w.expire(now, windowMillis);
                if (w.size() == 0) {
                    it.remove();
                }
            }
        }
    }

    private static final class Window {
        private long[] times = new long[8];
        private int head;
        private int size;

        int size() {
            return size;
        }

        long oldest() {
            return times[head];
        }

        void add(long t) {
            if (size == times.length) {
                long[] bigger = new long[times.length * 2];
                for (int i = 0; i < size; i++) {
                    bigger[i] = times[(head + i) % times.length];
                }
                times = bigger;
                head = 0;
            }
            times[(head + size) % times.length] = t;
            size++;
        }

        void expire(long now, long windowMillis) {
            while (size > 0 && now - times[head] >= windowMillis) {
                head = (head + 1) % times.length;
                size--;
            }
        }
    }
}
