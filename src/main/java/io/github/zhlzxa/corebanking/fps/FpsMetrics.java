package io.github.zhlzxa.corebanking.fps;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** Latency and outcome of every call to the FPS network. */
@Component
class FpsMetrics {

    static final String CALLS = "corebanking.fps.calls";

    private final MeterRegistry registry;

    FpsMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Calls FPS and records the latency, tagged with the operation and its outcome. A call that gets no
     * answer is recorded as {@code no_response} and the exception is rethrown.
     */
    <T> T time(String operation, Supplier<T> call, Function<T, String> outcome) {
        Timer.Sample sample = Timer.start(registry);
        String result = "error";
        try {
            T value = call.get();
            result = outcome.apply(value);
            return value;
        } catch (FpsCommunicationException ex) {
            result = "no_response";
            throw ex;
        } finally {
            sample.stop(Timer.builder(CALLS)
                    .description("Calls to the FPS network")
                    .tag("operation", operation)
                    .tag("outcome", result)
                    .register(registry));
        }
    }
}
