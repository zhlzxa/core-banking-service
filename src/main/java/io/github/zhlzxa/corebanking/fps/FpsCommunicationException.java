package io.github.zhlzxa.corebanking.fps;

/**
 * No answer was received from FPS, for example because a timeout elapsed. The outcome of the
 * request is unknown and must be resolved by querying FPS later.
 */
public class FpsCommunicationException extends RuntimeException {

    public FpsCommunicationException(String message) {
        super(message);
    }
}
