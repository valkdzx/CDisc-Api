package dev.valkdz.cdiscapi.api.event;

import dev.valkdz.cdiscapi.api.SoundHandle;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class SoundStartEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final SoundHandle sound;

    public SoundStartEvent(SoundHandle sound) {
        this.sound = sound;
    }

    public SoundHandle getSound() {
        return sound;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
