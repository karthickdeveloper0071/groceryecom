package com.groceryecom.platform.ratelimit;

import com.groceryecom.platform.ratelimit.RateLimitProperties.Policy;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRateLimiterTest {

    private static final Instant NOW = Instant.parse("2026-09-18T10:00:00Z");
    private static final Policy THREE_PER_MINUTE = new Policy(3, Duration.ofMinutes(1));

    @Test
    void allowsUpToTheLimitThenBlocks() {
        RateLimiter limiter = limiterAt(NOW);

        assertThat(limiter.check("k", THREE_PER_MINUTE).allowed()).isTrue();
        assertThat(limiter.check("k", THREE_PER_MINUTE).allowed()).isTrue();
        assertThat(limiter.check("k", THREE_PER_MINUTE).allowed()).isTrue();

        RateLimiter.Decision blocked = limiter.check("k", THREE_PER_MINUTE);
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.retryAfterSeconds()).isEqualTo(60);
    }

    @Test
    void countsEachKeySeparately() {
        RateLimiter limiter = limiterAt(NOW);

        limiter.check("ip:1", THREE_PER_MINUTE);
        limiter.check("ip:1", THREE_PER_MINUTE);
        limiter.check("ip:1", THREE_PER_MINUTE);

        assertThat(limiter.check("ip:2", THREE_PER_MINUTE).allowed()).isTrue();
    }

    @Test
    void startsCountingAgainAfterTheWindow() {
        MutableClock clock = new MutableClock(NOW);
        RateLimiter limiter = new InMemoryRateLimiter(clock);

        for (int attempt = 0; attempt < 4; attempt++) {
            limiter.check("k", THREE_PER_MINUTE);
        }
        assertThat(limiter.check("k", THREE_PER_MINUTE).allowed()).isFalse();

        clock.advance(Duration.ofMinutes(1).plusSeconds(1));

        assertThat(limiter.check("k", THREE_PER_MINUTE).allowed()).isTrue();
    }

    @Test
    void retryAfterShrinksAsTheWindowRunsOut() {
        MutableClock clock = new MutableClock(NOW);
        RateLimiter limiter = new InMemoryRateLimiter(clock);
        for (int attempt = 0; attempt < 4; attempt++) {
            limiter.check("k", THREE_PER_MINUTE);
        }

        clock.advance(Duration.ofSeconds(45));

        assertThat(limiter.check("k", THREE_PER_MINUTE).retryAfterSeconds()).isEqualTo(15);
    }

    private static RateLimiter limiterAt(Instant instant) {
        return new InMemoryRateLimiter(Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
