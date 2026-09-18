package io.github.zhlzxa.corebanking.transaction;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * Position in a transaction history, identifying the last row of the previous page.
 *
 * <p>History is ordered by {@code (created_at DESC, id DESC)}. The id is part of the position
 * because several transactions can share a timestamp; a cursor on the timestamp alone would skip
 * or repeat rows at page boundaries.
 *
 * <p>The encoded form is opaque to clients. It is Base64 for transport convenience, not for
 * secrecy, and is therefore validated as untrusted input when decoded.
 */
public record HistoryCursor(Instant createdAt, long transactionId) {

    private static final int MAX_ENCODED_LENGTH = 200;

    public String encode() {
        String raw = createdAt + "|" + transactionId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @throws InvalidCursorException if the value was not produced by {@link #encode()}
     */
    public static HistoryCursor decode(String encoded) {
        if (encoded == null || encoded.isBlank() || encoded.length() > MAX_ENCODED_LENGTH) {
            throw new InvalidCursorException();
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            int separator = raw.indexOf('|');
            if (separator < 0) {
                throw new InvalidCursorException();
            }
            Instant createdAt = Instant.parse(raw.substring(0, separator));
            long transactionId = Long.parseLong(raw.substring(separator + 1));
            if (transactionId <= 0) {
                throw new InvalidCursorException();
            }
            return new HistoryCursor(createdAt, transactionId);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            // NumberFormatException is an IllegalArgumentException, as is invalid Base64.
            throw new InvalidCursorException();
        }
    }
}
