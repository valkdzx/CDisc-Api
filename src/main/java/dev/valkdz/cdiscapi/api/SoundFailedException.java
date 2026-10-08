package dev.valkdz.cdiscapi.api;

public class SoundFailedException extends RuntimeException {

    private final SoundEndReason reason;

    public SoundFailedException(SoundEndReason reason, Throwable cause) {
        super(reason.name(), cause);
        this.reason = reason;
    }

    public SoundEndReason reason() {
        return reason;
    }
}
