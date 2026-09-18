package io.github.zhlzxa.corebanking.web;

import io.github.zhlzxa.corebanking.common.error.ErrorCode;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.ProblemDetail;

/** Builds the RFC 9457 problem responses used for every error the API returns. */
public final class ProblemDetails {

    private static final String TYPE_BASE = "https://corebanking.example/problems/";

    private ProblemDetails() {}

    /**
     * Creates a problem for a catalogued error code, with the request's correlation id attached.
     *
     * @param detail fixed, client-safe sentence; must never contain internal details or echoed input
     */
    public static ProblemDetail of(ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.httpStatus(), detail);
        problem.setType(
                URI.create(TYPE_BASE + code.name().toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setTitle(code.title());
        problem.setProperty("code", code.name());
        withCorrelationId(problem);
        return problem;
    }

    public static void withCorrelationId(ProblemDetail problem) {
        CorrelationId.current().ifPresent(id -> problem.setProperty("correlationId", id));
    }

    /** Flattens a problem into the JSON member layout defined by RFC 9457. */
    public static Map<String, Object> toJsonMembers(ProblemDetail problem) {
        Map<String, Object> members = new LinkedHashMap<>();
        members.put("type", problem.getType().toString());
        members.put("title", problem.getTitle());
        members.put("status", problem.getStatus());
        members.put("detail", problem.getDetail());
        if (problem.getInstance() != null) {
            members.put("instance", problem.getInstance().toString());
        }
        if (problem.getProperties() != null) {
            members.putAll(problem.getProperties());
        }
        return members;
    }
}
