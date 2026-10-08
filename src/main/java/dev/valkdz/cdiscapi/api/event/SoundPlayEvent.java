package dev.valkdz.cdiscapi.api.event;

import dev.valkdz.cdiscapi.api.SoundRequest;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

// Fired on whichever thread called play(), so it is asynchronous when that thread is not a tick thread.
public class SoundPlayEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String id;
    private SoundRequest request;
    private boolean cancelled;

    public SoundPlayEvent(String id, SoundRequest request, boolean async) {
        super(async);
        this.id = id;
        this.request = request;
    }

    public String getId() {
        return id;
    }

    public SoundRequest getRequest() {
        return request;
    }

    public void setRequest(SoundRequest request) {
        if (request == null) throw new IllegalArgumentException("request");
        this.request = request;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
