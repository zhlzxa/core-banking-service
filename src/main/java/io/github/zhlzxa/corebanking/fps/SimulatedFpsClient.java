package io.github.zhlzxa.corebanking.fps;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * In-memory stand-in for the FPS network, used for local runs and demonstrations.
 *
 * <p>The creditor account number selects the behaviour, so that every failure mode can be
 * reproduced on demand:
 *
 * <ul>
 *   <li>{@code REJECT...}: FPS rejects the payment;
 *   <li>{@code TIMEOUT...}: FPS accepts the payment but the response is lost, so the sender sees a
 *       timeout although the money was paid;
 *   <li>{@code LOST...}: the first request never reaches FPS; the sender sees a timeout and FPS
 *       knows nothing about the payment until it is sent again;
 *   <li>anything else: FPS accepts the payment.
 * </ul>
 *
 * <p>Like the real network, a payment is recorded once per end-to-end id: sending the same id again
 * returns the original decision.
 */
@Component
@ConditionalOnProperty(name = "corebanking.fps.client", havingValue = "simulated", matchIfMissing = true)
public class SimulatedFpsClient implements FpsClient {

    private final Map<String, Status> payments = new ConcurrentHashMap<>();
    private final Set<String> lostOnce = ConcurrentHashMap.newKeySet();

    @Override
    public Decision send(FpsInstruction instruction) {
        Status known = payments.get(instruction.endToEndId());
        if (known != null) {
            return known == Status.ACCEPTED ? Decision.accept() : Decision.reject("DUPLICATE_OF_REJECTED");
        }
        String creditor = instruction.creditorAccount();
        if (creditor.startsWith("LOST") && lostOnce.add(instruction.endToEndId())) {
            throw new FpsCommunicationException("Simulated: request lost before reaching FPS");
        }
        if (creditor.startsWith("REJECT")) {
            payments.put(instruction.endToEndId(), Status.REJECTED);
            return Decision.reject("ACCOUNT_CLOSED");
        }
        payments.put(instruction.endToEndId(), Status.ACCEPTED);
        if (creditor.startsWith("TIMEOUT")) {
            throw new FpsCommunicationException("Simulated: response lost after FPS accepted the payment");
        }
        return Decision.accept();
    }

    @Override
    public Status query(String endToEndId) {
        return payments.getOrDefault(endToEndId, Status.NOT_FOUND);
    }
}
