package io.github.zhlzxa.corebanking.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HistoryCursorTest {

    @Test
    void roundTripsWithMicrosecondPrecision() {
        HistoryCursor cursor = new HistoryCursor(Instant.parse("2026-09-18T08:00:00.123456Z"), 42);

        assertThat(HistoryCursor.decode(cursor.encode())).isEqualTo(cursor);
    }

    @Test
    void encodedFormIsUrlSafe() {
        String encoded = new HistoryCursor(Instant.parse("2026-09-18T08:00:00Z"), Long.MAX_VALUE).encode();

        assertThat(encoded).matches("[A-Za-z0-9_-]+");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not base64 !", "abc"})
    void rejectsGarbage(String value) {
        assertThatThrownBy(() -> HistoryCursor.decode(value)).isInstanceOf(InvalidCursorException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-09-18T08:00:00Z", "yesterday|5", "2026-09-18T08:00:00Z|x", "2026-09-18T08:00:00Z|-1"})
    void rejectsWellEncodedButInvalidContent(String raw) {
        String encoded = Base64.getUrlEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> HistoryCursor.decode(encoded)).isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void rejectsOverlongInput() {
        assertThatThrownBy(() -> HistoryCursor.decode("A".repeat(201))).isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> HistoryCursor.decode(null)).isInstanceOf(InvalidCursorException.class);
    }
}
