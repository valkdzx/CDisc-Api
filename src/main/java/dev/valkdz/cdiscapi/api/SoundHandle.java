package dev.valkdz.cdiscapi.api;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface SoundHandle {

    enum State {
        LOADING,
        PLAYING,
        PAUSED,
        ENDED
    }

    String id();

    String source();

    Plugin owner();

    State state();

    default boolean isActive() {
        return state() != State.ENDED;
    }

    String title();

    String author();

    String uri();

    boolean live();

    long position();

    long duration();

    Location location();

    Entity following();

    UUID listener();

    int volume();

    void volume(int volume);

    float distance();

    void distance(float distance);

    boolean looping();

    void loop(boolean loop);

    void pause();

    void resume();

    void seek(long positionMs);

    void moveTo(Location location);

    void follow(Entity entity);

    void stop();

    CompletableFuture<SoundHandle> started();

    CompletableFuture<SoundEndReason> ended();
}
