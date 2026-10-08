package dev.valkdz.cdiscapi.api;

public class SoundRefusedException extends RuntimeException {

    public enum Reason {
        CDISC_DISABLED,
        NO_VOICE,
        LIMIT,
        CANCELLED
    }

    private final Reason reason;

    public SoundRefusedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
