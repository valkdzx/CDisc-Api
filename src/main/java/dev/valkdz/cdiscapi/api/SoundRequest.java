package dev.valkdz.cdiscapi.api;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.UUID;

public final class SoundRequest {

    public static final int DEFAULT = -1;

    private final String source;
    private final String id;
    private final Location location;
    private final Entity following;
    private final UUID listener;
    private final int volume;
    private final float distance;
    private final boolean loop;
    private final long startAtMs;
    private final String title;
    private final String author;
    private final Plugin owner;

    private SoundRequest(Builder b) {
        this.source = b.source;
        this.id = b.id;
        this.location = b.location == null ? null : b.location.clone();
        this.following = b.following;
        this.listener = b.listener;
        this.volume = b.volume;
        this.distance = b.distance;
        this.loop = b.loop;
        this.startAtMs = b.startAtMs;
        this.title = b.title;
        this.author = b.author;
        this.owner = b.owner;
    }

    public static Builder builder(String source) {
        return new Builder(source);
    }

    public String source() {
        return source;
    }

    public String id() {
        return id;
    }

    public Location location() {
        return location == null ? null : location.clone();
    }

    public Entity following() {
        return following;
    }

    public UUID listener() {
        return listener;
    }

    public int volume() {
        return volume;
    }

    public float distance() {
        return distance;
    }

    public boolean loop() {
        return loop;
    }

    public long startAtMs() {
        return startAtMs;
    }

    public String title() {
        return title;
    }

    public String author() {
        return author;
    }

    public Plugin owner() {
        return owner;
    }

    public Builder toBuilder() {
        Builder b = new Builder(source);
        b.id = id;
        b.location = location;
        b.following = following;
        b.listener = listener;
        b.volume = volume;
        b.distance = distance;
        b.loop = loop;
        b.startAtMs = startAtMs;
        b.title = title;
        b.author = author;
        b.owner = owner;
        return b;
    }

    public static final class Builder {
        private final String source;
        private String id;
        private Location location;
        private Entity following;
        private UUID listener;
        private int volume = DEFAULT;
        private float distance = DEFAULT;
        private boolean loop;
        private long startAtMs;
        private String title;
        private String author;
        private Plugin owner;

        private Builder(String source) {
            if (source == null || source.isBlank()) throw new IllegalArgumentException("source is empty");
            this.source = source.trim();
        }

        public Builder id(String id) {
            this.id = id == null || id.isBlank() ? null : id.trim();
            return this;
        }

        public Builder at(Location location) {
            this.location = Objects.requireNonNull(location, "location");
            this.following = null;
            return this;
        }

        public Builder following(Entity entity) {
            this.following = Objects.requireNonNull(entity, "entity");
            this.location = null;
            return this;
        }

        public Builder listener(Player player) {
            return listener(player == null ? null : player.getUniqueId());
        }

        public Builder listener(UUID player) {
            this.listener = player;
            return this;
        }

        public Builder volume(int volume) {
            this.volume = volume;
            return this;
        }

        public Builder distance(float distance) {
            this.distance = distance;
            return this;
        }

        public Builder loop(boolean loop) {
            this.loop = loop;
            return this;
        }

        public Builder startAt(long positionMs) {
            this.startAtMs = Math.max(0L, positionMs);
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder author(String author) {
            this.author = author;
            return this;
        }

        public Builder owner(Plugin owner) {
            this.owner = owner;
            return this;
        }

        public SoundRequest build() {
            if (location == null && following == null) {
                throw new IllegalStateException("a sound needs at(location) or following(entity)");
            }
            if (location != null && location.getWorld() == null) {
                throw new IllegalStateException("the location has no world");
            }
            return new SoundRequest(this);
        }
    }
}
