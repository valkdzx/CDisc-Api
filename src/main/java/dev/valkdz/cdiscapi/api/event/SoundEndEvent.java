package dev.valkdz.cdiscapi.api.event;

import dev.valkdz.cdiscapi.api.SoundEndReason;
import dev.valkdz.cdiscapi.api.SoundHandle;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class SoundEndEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final SoundHandle sound;
    private final SoundEndReason reason;

    public SoundEndEvent(SoundHandle sound, SoundEndReason reason) {
        this.sound = sound;
        this.reason = reason;
    }

    public SoundHandle getSound() {
        return sound;
    }

    public SoundEndReason getReason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
