package io.github.zhlzxa.corebanking.web;

import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/**
 * Identifier that ties together everything that happens for one request: log lines, error
 * responses and audit records. It is accepted from the caller when well-formed, so that a request
 * can be traced across systems, and generated otherwise.
 */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    /** Accepted characters and length; anything else is replaced rather than logged verbatim. */
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{1,100}");

    private CorrelationId() {}

    public static boolean isValid(String candidate) {
        return candidate != null && VALID.matcher(candidate).matches();
    }

    /** The correlation id of the request being processed on the current thread, if any. */
    public static Optional<String> current() {
        return Optional.ofNullable(MDC.get(MDC_KEY));
    }
}
