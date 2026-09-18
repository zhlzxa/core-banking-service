package io.github.zhlzxa.corebanking.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void reusesWellFormedCallerSuppliedId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationId.HEADER, "abc-123.X_y");

        String seen = run(request, new MockHttpServletResponse());

        assertThat(seen).isEqualTo("abc-123.X_y");
    }

    @Test
    void replacesMalformedIdInsteadOfLoggingIt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationId.HEADER, "bad id\nINJECTED LOG LINE");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String seen = run(request, response);

        assertThat(seen).doesNotContain("INJECTED").hasSize(36);
        assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo(seen);
    }

    @Test
    void rejectsOverlongIds() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationId.HEADER, "a".repeat(101));

        assertThat(run(request, new MockHttpServletResponse())).hasSize(36);
    }

    @Test
    void generatesIdWhenMissingAndEchoesIt() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        String seen = run(new MockHttpServletRequest(), response);

        assertThat(seen).isNotBlank();
        assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo(seen);
    }

    @Test
    void clearsTheLoggingContextAfterTheRequest() throws Exception {
        run(new MockHttpServletRequest(), new MockHttpServletResponse());

        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
        assertThat(CorrelationId.current()).isEmpty();
    }

    /** Runs the filter and returns the correlation id visible to downstream code. */
    private String run(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seen.set(CorrelationId.current().orElse(null));
            }
        };
        filter.doFilter(request, response, chain);
        return seen.get();
    }
}
