package io.github.zhlzxa.corebanking.fps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.zhlzxa.corebanking.fps.FpsClient.Status;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SimulatedFpsClientTest {

    private final SimulatedFpsClient fps = new SimulatedFpsClient();

    @Test
    void acceptsOrdinaryPayments() {
        assertThat(fps.send(instruction("e2e-1", "123456")).accepted()).isTrue();
        assertThat(fps.query("e2e-1")).isEqualTo(Status.ACCEPTED);
    }

    @Test
    void rejectsOnRequest() {
        FpsClient.Decision decision = fps.send(instruction("e2e-1", "REJECT-1"));

        assertThat(decision.accepted()).isFalse();
        assertThat(decision.rejectionReason()).isEqualTo("ACCOUNT_CLOSED");
        assertThat(fps.query("e2e-1")).isEqualTo(Status.REJECTED);
    }

    @Test
    void aTimeoutCanHideAnAcceptedPayment() {
        assertThatThrownBy(() -> fps.send(instruction("e2e-1", "TIMEOUT-1")))
                .isInstanceOf(FpsCommunicationException.class);

        assertThat(fps.query("e2e-1")).isEqualTo(Status.ACCEPTED);
    }

    @Test
    void aLostRequestIsUnknownToFps() {
        assertThatThrownBy(() -> fps.send(instruction("e2e-1", "LOST-1")))
                .isInstanceOf(FpsCommunicationException.class);

        assertThat(fps.query("e2e-1")).isEqualTo(Status.NOT_FOUND);
    }

    @Test
    void resendingTheSameEndToEndIdReturnsTheOriginalDecision() {
        fps.send(instruction("e2e-1", "123456"));

        assertThat(fps.send(instruction("e2e-1", "123456")).accepted()).isTrue();
    }

    private static FpsInstruction instruction(String endToEndId, String creditorAccount) {
        return new FpsInstruction(endToEndId, 100, "004", creditorAccount, new BigDecimal("10.00"), "HKD");
    }
}
