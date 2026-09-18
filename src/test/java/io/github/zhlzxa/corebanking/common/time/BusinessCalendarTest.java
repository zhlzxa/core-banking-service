package io.github.zhlzxa.corebanking.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class BusinessCalendarTest {

    private static final ZoneId HONG_KONG = ZoneId.of("Asia/Hong_Kong");

    @Test
    void businessDayFollowsHongKongMidnightNotUtc() {
        // 16:00 UTC is midnight in Hong Kong (UTC+8).
        assertThat(calendarAt("2026-09-18T15:59:59Z").today()).isEqualTo(LocalDate.of(2026, 9, 18));
        assertThat(calendarAt("2026-09-18T16:00:00Z").today()).isEqualTo(LocalDate.of(2026, 9, 19));
    }

    @Test
    void earlyMorningInHongKongIsStillThePreviousDayInUtc() {
        BusinessCalendar calendar = calendarAt("2026-09-18T17:30:00Z");

        assertThat(calendar.today()).isEqualTo(LocalDate.of(2026, 9, 19));
        assertThat(LocalDate.ofInstant(Instant.parse("2026-09-18T17:30:00Z"), ZoneOffset.UTC))
                .isEqualTo(LocalDate.of(2026, 9, 18));
    }

    private static BusinessCalendar calendarAt(String instant) {
        return new BusinessCalendar(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC), HONG_KONG);
    }
}
