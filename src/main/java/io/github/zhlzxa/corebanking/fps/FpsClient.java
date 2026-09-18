package io.github.zhlzxa.corebanking.fps;

import org.jspecify.annotations.Nullable;

/**
 * Boundary to the FPS payment network.
 *
 * <p>Calls are network calls with bounded timeouts and must never be made while holding database
 * locks or inside a database transaction. A {@link FpsCommunicationException} means the outcome is
 * unknown, not that the payment failed: FPS may well have processed it.
 */
public interface FpsClient {

    /**
     * Submits a payment. Submitting the same end-to-end id again returns the original decision.
     *
     * @throws FpsCommunicationException if no decision was received, for example on timeout
     */
    Decision send(FpsInstruction instruction);

    /**
     * Asks FPS what happened to a payment.
     *
     * @throws FpsCommunicationException if FPS could not be reached
     */
    Status query(String endToEndId);

    /** FPS's decision on a submitted payment. */
    record Decision(boolean accepted, @Nullable String rejectionReason) {

        public static Decision accept() {
            return new Decision(true, null);
        }

        public static Decision reject(String reason) {
            return new Decision(false, reason);
        }
    }

    /** What FPS knows about a payment. */
    enum Status {
        ACCEPTED,
        REJECTED,
        /** Still being processed at FPS; ask again later. */
        PENDING,
        /** FPS never received the payment; it is safe to send it again with the same id. */
        NOT_FOUND
    }
}
