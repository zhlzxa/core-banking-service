package io.github.zhlzxa.corebanking.common.time;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Determines the bank's business day.
 *
 * <p>Daily limits reset at midnight in the bank's home time zone, not in UTC and not in the
 * server's default zone: a transfer at 01:00 in Hong Kong belongs to the new Hong Kong day even
 * though it is still the previous day in UTC. The day is a calendar day; public holidays and
 * settlement calendars are not modelled.
 */
@Component
public class BusinessCalendar {

    private final Clock clock;
    private final ZoneId zone;

    @Autowired
    public BusinessCalendar(Clock clock, @Value("${corebanking.business-day.zone}") String zone) {
        this(clock, ZoneId.of(zone));
    }

    public BusinessCalendar(Clock clock, ZoneId zone) {
        this.clock = clock;
        this.zone = zone;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }
}
