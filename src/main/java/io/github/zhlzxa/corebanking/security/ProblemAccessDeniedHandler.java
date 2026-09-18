package io.github.zhlzxa.corebanking.security;

import io.github.zhlzxa.corebanking.common.error.ErrorCode;
import io.github.zhlzxa.corebanking.web.ProblemDetails;
import io.github.zhlzxa.corebanking.web.ProblemResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Answers requests rejected by URL-level authorization rules with 403 and a problem body. */
@Component
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    private final BearerTokenAccessDeniedHandler bearerHandler = new BearerTokenAccessDeniedHandler();
    private final ProblemResponseWriter writer;

    public ProblemAccessDeniedHandler(ProblemResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        bearerHandler.handle(request, response, ex);
        writer.write(
                request,
                response,
                ProblemDetails.of(ErrorCode.ACCESS_DENIED, "The caller is not permitted to perform this operation"));
    }
}
