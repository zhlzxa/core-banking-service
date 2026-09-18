package io.github.zhlzxa.corebanking.common.time;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the application clock. Business timestamps are taken from this bean rather than from
 * {@code Instant.now()} so that time-dependent rules can be tested deterministically.
 */
@Configuration
class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
