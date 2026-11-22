package dan.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class FailedAttemptLimiterTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final FailedAttemptLimiter limiter = new FailedAttemptLimiter(3, Duration.ofMinutes(1), now::get);

    @Test
    void underTheLimit_ShouldNotBlock() {
        limiter.recordFailure("a");
        limiter.recordFailure("a");

        assertThat(limiter.isBlocked("a")).isFalse();
        assertThat(limiter.retryAfterSeconds("a")).isZero();
    }

    @Test
    void reachingTheLimit_ShouldBlockAndReportRemainingSeconds() {
        limiter.recordFailure("a");
        now.addAndGet(10_000);
        limiter.recordFailure("a");
        limiter.recordFailure("a");

        assertThat(limiter.isBlocked("a")).isTrue();
        // el primer fallo sale de la ventana 60 s despues de haber ocurrido: faltan 50 s
        assertThat(limiter.retryAfterSeconds("a")).isEqualTo(50);
    }

    @Test
    void blockedKey_ShouldRecoverWhenTheOldestFailureLeavesTheWindow() {
        limiter.recordFailure("a");
        limiter.recordFailure("a");
        limiter.recordFailure("a");
        assertThat(limiter.isBlocked("a")).isTrue();

        now.addAndGet(59_999);
        assertThat(limiter.isBlocked("a")).isTrue();
        now.addAndGet(1);
        assertThat(limiter.isBlocked("a")).isFalse();
    }

    @Test
    void keysAreIndependent() {
        for (int i = 0; i < 3; i++) {
            limiter.recordFailure("a");
        }

        assertThat(limiter.isBlocked("a")).isTrue();
        assertThat(limiter.isBlocked("b")).isFalse();
    }

    @Test
    void reset_ShouldClearTheKey() {
        for (int i = 0; i < 3; i++) {
            limiter.recordFailure("a");
        }
        limiter.reset("a");

        assertThat(limiter.isBlocked("a")).isFalse();
        assertThat(limiter.trackedKeys()).isZero();
    }

    @Test
    void failuresOutsideTheWindow_ShouldNotAccumulate() {
        limiter.recordFailure("a");
        limiter.recordFailure("a");
        now.addAndGet(61_000);
        limiter.recordFailure("a");

        assertThat(limiter.isBlocked("a")).isFalse();
    }

    @Test
    void manyFailuresWithinTheWindow_ShouldGrowTheBufferWithoutLosingCount() {
        FailedAttemptLimiter big = new FailedAttemptLimiter(50, Duration.ofMinutes(1), now::get);
        for (int i = 0; i < 49; i++) {
            big.recordFailure("a");
        }
        assertThat(big.isBlocked("a")).isFalse();
        big.recordFailure("a");
        assertThat(big.isBlocked("a")).isTrue();
    }

    @Test
    void concurrentFailures_ShouldBeCountedExactly() throws Exception {
        FailedAttemptLimiter concurrent = new FailedAttemptLimiter(100, Duration.ofMinutes(1), now::get);
        Thread[] threads = new Thread[20];
        for (int t = 0; t < threads.length; t++) {
            threads[t] = new Thread(() -> {
                for (int i = 0; i < 4; i++) {
                    concurrent.recordFailure("a");
                }
            });
            threads[t].start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        // 80 fallos: todavia no llega a 100
        assertThat(concurrent.isBlocked("a")).isFalse();
        for (int i = 0; i < 20; i++) {
            concurrent.recordFailure("a");
        }
        assertThat(concurrent.isBlocked("a")).isTrue();
    }

    @Test
    void trackedKeys_ShouldBeBoundedAgainstKeyFlooding() {
        FailedAttemptLimiter flooded = new FailedAttemptLimiter(3, Duration.ofMinutes(1), now::get);
        for (int i = 0; i < 60_000; i++) {
            flooded.recordFailure("ip-" + i);
        }

        assertThat(flooded.trackedKeys()).isLessThanOrEqualTo(50_000);
    }

    @Test
    void expiredKeys_ShouldBeEvictedWhenTheMapIsFull() {
        FailedAttemptLimiter flooded = new FailedAttemptLimiter(3, Duration.ofMinutes(1), now::get);
        for (int i = 0; i < 50_000; i++) {
            flooded.recordFailure("ip-" + i);
        }
        now.addAndGet(120_000);
        flooded.recordFailure("nueva");

        assertThat(flooded.trackedKeys()).isLessThan(50_000);
        assertThat(flooded.isBlocked("nueva")).isFalse();
    }

    @Test
    void invalidConfiguration_ShouldBeRejected() {
        assertThatThrownBy(() -> new FailedAttemptLimiter(0, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FailedAttemptLimiter(3, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
