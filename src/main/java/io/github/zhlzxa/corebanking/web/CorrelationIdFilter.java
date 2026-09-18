package io.github.zhlzxa.corebanking.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Establishes the correlation id for every request before any other processing, including
 * authentication, so that even rejected requests can be traced.
 *
 * <p>A caller-supplied {@value CorrelationId#HEADER} is reused only if it is well-formed; untrusted
 * input is never written to logs as-is. The id is echoed in the response header and placed in the
 * logging MDC for the duration of the request.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader(CorrelationId.HEADER);
        String correlationId =
                CorrelationId.isValid(supplied) ? supplied : UUID.randomUUID().toString();
        MDC.put(CorrelationId.MDC_KEY, correlationId);
        response.setHeader(CorrelationId.HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            // Servlet threads are pooled; a leftover value would be attributed to the next request.
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }
}
