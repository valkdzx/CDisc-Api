package dev.valkdz.cdiscapi.core;

import dev.valkdz.cdisc.Main;
import dev.valkdz.cdisc.audio.player.AudioEventAdapter;
import dev.valkdz.cdisc.audio.player.AudioLoadResultHandler;
import dev.valkdz.cdisc.audio.player.AudioPlayer;
import dev.valkdz.cdisc.audio.player.AudioPlaylist;
import dev.valkdz.cdisc.audio.player.AudioTrack;
import dev.valkdz.cdisc.audio.player.AudioTrackEndReason;
import dev.valkdz.cdisc.audio.player.AudioTrackInfo;
import dev.valkdz.cdisc.audio.player.LoadException;
import dev.valkdz.cdisc.audio.source.TrackLoader;
import dev.valkdz.cdisc.feature.local.LocalTrackSettings;
import dev.valkdz.cdisc.jukebox.AudioSession;
import dev.valkdz.cdisc.jukebox.PlaybackManager;
import dev.valkdz.cdisc.util.Tasks;
import dev.valkdz.cdisc.voice.VoiceSession;
import dev.valkdz.cdisc.voice.anchor.SoundAnchor;
import dev.valkdz.cdiscapi.api.SoundEndReason;
import dev.valkdz.cdiscapi.api.SoundFailedException;
import dev.valkdz.cdiscapi.api.SoundHandle;
import dev.valkdz.cdiscapi.api.SoundRequest;
import dev.valkdz.cdiscapi.api.event.SoundEndEvent;
import dev.valkdz.cdiscapi.api.event.SoundStartEvent;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class ApiSound implements SoundHandle {

    private final SoundService service;
    private final Plugin plugin;
    private final String id;
    private final SoundRequest request;
    private final UUID listener;
    private final boolean volumeGiven;
    private final long createdAt = System.currentTimeMillis();

    private volatile Location location;
    private volatile Entity following;
    private volatile int volume;
    private volatile float distance;
    private volatile boolean loop;
    private volatile boolean pauseWanted;

    private volatile String resolved;
    private volatile String shownTitle;
    private volatile String shownAuthor;
    private volatile SoundAnchor anchor;
    private volatile AudioPlayer player;
    private volatile AudioTrack track;
    private final AtomicReference<AudioSession> session = new AtomicReference<>();

    private volatile State state = State.LOADING;
    private final AtomicBoolean ended = new AtomicBoolean();
    private final CompletableFuture<SoundHandle> started = new CompletableFuture<>();
    private final CompletableFuture<SoundEndReason> endedFuture = new CompletableFuture<>();

    ApiSound(SoundService service, Plugin plugin, String id, SoundRequest request,
             int volume, float distance, boolean volumeGiven) {
        this.service = service;
        this.plugin = plugin;
        this.id = id;
        this.request = request;
        this.listener = request.listener();
        this.location = request.location();
        this.following = request.following();
        this.volume = volume;
        this.distance = distance;
        this.loop = request.loop();
        this.volumeGiven = volumeGiven;
        this.shownTitle = request.title();
        this.shownAuthor = request.author();
    }

    void begin() {
        Entity target = following;
        if (target != null) {
            Tasks.entity(plugin, target, this::open);
        } else {
            Tasks.region(plugin, location, this::open);
        }
    }

    // Runs on the thread that owns the spot, because the anchor entity is spawned here.
    private void open() {
        if (ended.get()) return;
        PlaybackManager apm = SoundService.manager();
        if (apm == null) {
            finish(SoundEndReason.SHUTDOWN, null);
            return;
        }
        if (!apm.hasVoiceBackend()) {
            finish(SoundEndReason.NO_VOICE, null);
            return;
        }

        TrackLoader loader = apm.getTrackLoader();
        String query = request.source();
        resolved = loader.resolveQuery(query);
        if (resolved == null) {
            finish(SoundEndReason.NO_MATCH, null);
            return;
        }
        LocalTrackSettings.Shown shown = Main.getInstance().getLocalMusic()
                .shown(query, request.title(), request.author());
        shownTitle = shown.title();
        shownAuthor = shown.author();

        Location at = origin();
        if (at == null || at.getWorld() == null) {
            finish(SoundEndReason.TARGET_GONE, null);
            return;
        }

        SoundAnchor fresh = apm.getAnchorManager().createAt(at);
        VoiceSession voice = apm.createFollowingSession(fresh.entity(), distance);
        if (voice == null) {
            fresh.remove();
            finish(SoundEndReason.NO_VOICE, null);
            return;
        }
        if (following != null) fresh.setTeleportSmoothing(2);
        if (listener != null) voice.setPrivateListener(listener);
        anchor = fresh;

        AudioPlayer audio = loader.createPlayer();
        audio.setPaused(pauseWanted);
        player = audio;
        session.set(new AudioSession(audio, voice, shownTitle, shownAuthor));

        // A stop() that landed while this ran found nothing to release yet.
        if (ended.get()) {
            release();
            return;
        }
        loader.load(resolved, null, shownTitle, shownAuthor, new Loaded());
    }

    private final class Loaded implements AudioLoadResultHandler {
        @Override
        public void trackLoaded(AudioTrack loaded) {
            AudioPlayer audio = player;
            if (ended.get() || audio == null) return;

            long start = request.startAtMs();
            if (start > 0 && loaded.isSeekable() && start < loaded.getDuration()) loaded.setPosition(start);
            if (!volumeGiven) {
                LocalTrackSettings own = Main.getInstance().getLocalMusic().settingsOf(loaded);
                if (own != null && own.volume() != null) volume = own.volume();
            }

            audio.addListener(events);
            audio.setVolume(volume);
            track = loaded;
            audio.playTrack(loaded);
            state = pauseWanted ? State.PAUSED : State.PLAYING;
            started.complete(ApiSound.this);
            service.fire(new SoundStartEvent(ApiSound.this));
        }

        @Override
        public void playlistLoaded(AudioPlaylist playlist) {
            if (playlist.getTracks().isEmpty()) {
                finish(SoundEndReason.NO_MATCH, null);
            } else {
                trackLoaded(playlist.getTracks().get(0));
            }
        }

        @Override
        public void noMatches() {
            finish(SoundEndReason.NO_MATCH, null);
        }

        @Override
        public void loadFailed(LoadException e) {
            finish(SoundEndReason.FAILED, e);
        }
    }

    private final AudioEventAdapter events = new AudioEventAdapter() {
        @Override
        public void onTrackEnd(AudioPlayer p, AudioTrack t, AudioTrackEndReason reason) {
            if (ended.get() || p != player) return;
            boolean done = reason == AudioTrackEndReason.FINISHED;
            boolean broken = reason == AudioTrackEndReason.LOAD_FAILED;

            AudioSession current = session.get();
            if (t.getInfo().isStream && (done || broken) && current != null && current.allowStreamReconnect()) {
                replay(p, t.makeClone());
                return;
            }

            PlaybackManager apm = SoundService.manager();
            TrackLoader loader = apm == null ? null : apm.getTrackLoader();
            if (broken && loader != null && loader.hasNextSource(t, resolved)) {
                loader.nextSourceAsync(t, resolved, replacement -> {
                    if (ended.get()) return;
                    if (replacement == null) {
                        finish(SoundEndReason.FAILED, null);
                    } else {
                        replay(p, replacement);
                    }
                });
                return;
            }

            if (done && loop) {
                replay(p, t.makeClone());
            } else if (done) {
                finish(SoundEndReason.FINISHED, null);
            } else if (broken) {
                finish(SoundEndReason.FAILED, null);
            }
        }

        @Override
        public void onTrackException(AudioPlayer p, AudioTrack t, LoadException e) {
            if (p == player) plugin.getLogger().warning("Sound " + id + " (" + t.getInfo().uri + "): "
                    + (e.getMessage() == null ? e : e.getMessage().split("\n", 2)[0]));
        }
    };

    private void replay(AudioPlayer p, AudioTrack next) {
        track = next;
        p.playTrack(next);
    }

    void follow() {
        Entity target = following;
        if (target == null || ended.get()) return;
        if (gone(target)) {
            finish(SoundEndReason.TARGET_GONE, null);
            return;
        }
        if (target instanceof Player p && p.isDead()) return;

        SoundAnchor current = anchor;
        if (current == null) return;
        Location at = pointOf(target);
        if (!current.inWorld(at.getWorld())) {
            rebind(current, at);
            current.setTeleportSmoothing(2);
            return;
        }
        current.followAt(at);
    }

    // The voice channel belongs to the anchor entity, which cannot cross worlds, so both are rebuilt.
    private void rebind(SoundAnchor current, Location at) {
        PlaybackManager apm = SoundService.manager();
        AudioSession playing = session.get();
        if (apm == null || playing == null) return;

        apm.getAnchorManager().moveTo(current, at);
        VoiceSession fresh = apm.createFollowingSession(current.entity(), distance);
        if (fresh == null) {
            finish(SoundEndReason.NO_VOICE, null);
            return;
        }
        if (listener != null) fresh.setPrivateListener(listener);
        playing.replaceVoiceSession(fresh);
    }

    boolean loadingTooLong(long now, long timeoutMs) {
        return state == State.LOADING && now - createdAt > timeoutMs;
    }

    boolean concerns(UUID player) {
        Entity target = following;
        return player.equals(listener) || (target != null && player.equals(target.getUniqueId()));
    }

    void finish(SoundEndReason reason, Throwable error) {
        if (!ended.compareAndSet(false, true)) return;
        state = State.ENDED;
        service.forget(this);
        if (reason == SoundEndReason.SHUTDOWN) {
            release();
        } else {
            service.execute(this::release);
        }
        if (!started.isDone()) started.completeExceptionally(new SoundFailedException(reason, error));
        endedFuture.complete(reason);
        if (error != null) {
            plugin.getLogger().warning("Sound " + id + " (" + request.source() + ") failed: "
                    + (error.getMessage() == null ? error : error.getMessage().split("\n", 2)[0]));
        }
        service.fire(new SoundEndEvent(this, reason));
    }

    private void release() {
        AudioSession playing = session.getAndSet(null);
        if (playing != null) playing.stop();
        SoundAnchor current = anchor;
        anchor = null;
        if (current != null) current.remove();
    }

    private Location origin() {
        Entity target = following;
        if (target != null) return gone(target) ? null : pointOf(target);
        return location == null ? null : location.clone();
    }

    private static Location pointOf(Entity entity) {
        return entity.getLocation().add(0, entity.getHeight() * 0.5, 0);
    }

    private static boolean gone(Entity entity) {
        return entity instanceof Player p ? !p.isOnline() : !entity.isValid();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String source() {
        return request.source();
    }

    @Override
    public Plugin owner() {
        return request.owner();
    }

    @Override
    public State state() {
        return state;
    }

    @Override
    public String title() {
        if (shownTitle != null) return shownTitle;
        AudioTrack t = track;
        if (t != null && t.getInfo().title != null && !AudioTrackInfo.UNKNOWN_TITLE.equals(t.getInfo().title)) {
            return t.getInfo().title;
        }
        return request.source();
    }

    @Override
    public String author() {
        if (shownAuthor != null) return shownAuthor;
        AudioTrack t = track;
        if (t == null || t.getInfo().author == null || AudioTrackInfo.UNKNOWN_ARTIST.equals(t.getInfo().author)) {
            return null;
        }
        return t.getInfo().author;
    }

    @Override
    public String uri() {
        AudioTrack t = track;
        return t == null ? null : t.getInfo().uri;
    }

    @Override
    public boolean live() {
        AudioTrack t = track;
        return t != null && t.getInfo().isStream;
    }

    @Override
    public long position() {
        AudioTrack t = track;
        AudioSession playing = session.get();
        if (t == null) return 0L;
        return playing == null ? t.getPosition() : playing.positionOf(t);
    }

    @Override
    public long duration() {
        AudioTrack t = track;
        if (t == null || t.getInfo().isStream) return -1L;
        long length = t.getDuration();
        return length == AudioTrackInfo.UNKNOWN_LENGTH ? -1L : length;
    }

    @Override
    public Location location() {
        Entity target = following;
        if (target != null) return target.getLocation();
        return location == null ? null : location.clone();
    }

    @Override
    public Entity following() {
        return following;
    }

    @Override
    public UUID listener() {
        return listener;
    }

    @Override
    public int volume() {
        return volume;
    }

    @Override
    public void volume(int volume) {
        this.volume = service.clampVolume(volume);
        AudioPlayer audio = player;
        if (audio != null && track != null) audio.setVolume(this.volume);
    }

    @Override
    public float distance() {
        return distance;
    }

    @Override
    public void distance(float distance) {
        this.distance = service.clampDistance(distance);
        AudioSession playing = session.get();
        if (playing == null) return;
        for (VoiceSession output : playing.allOutputs()) output.setDistance(this.distance);
    }

    @Override
    public boolean looping() {
        return loop;
    }

    @Override
    public void loop(boolean loop) {
        this.loop = loop;
    }

    @Override
    public void pause() {
        setPaused(true);
    }

    @Override
    public void resume() {
        setPaused(false);
    }

    private void setPaused(boolean paused) {
        pauseWanted = paused;
        AudioPlayer audio = player;
        if (audio != null) audio.setPaused(paused);
        if (state != State.LOADING && state != State.ENDED) state = paused ? State.PAUSED : State.PLAYING;
    }

    @Override
    public void seek(long positionMs) {
        AudioTrack t = track;
        AudioSession playing = session.get();
        if (t == null || playing == null || !t.isSeekable()) return;
        long length = duration();
        long clamped = Math.max(0L, length > 0 ? Math.min(positionMs, length) : positionMs);
        playing.seeking(t, clamped);
        t.setPosition(clamped);
    }

    @Override
    public void moveTo(Location where) {
        if (where == null || where.getWorld() == null) throw new IllegalArgumentException("location");
        Location target = where.clone();
        following = null;
        location = target;

        SoundAnchor current = anchor;
        if (current == null || ended.get()) return;
        Tasks.region(plugin, target, () -> {
            if (ended.get() || anchor != current) return;
            current.setTeleportSmoothing(0);
            if (current.inWorld(target.getWorld())) {
                current.parkAt(target);
            } else {
                rebind(current, target);
            }
        });
    }

    @Override
    public void follow(Entity entity) {
        if (entity == null) throw new IllegalArgumentException("entity");
        following = entity;
        location = null;
        SoundAnchor current = anchor;
        if (current != null) current.setTeleportSmoothing(2);
    }

    @Override
    public void stop() {
        finish(SoundEndReason.STOPPED, null);
    }

    @Override
    public CompletableFuture<SoundHandle> started() {
        return started;
    }

    @Override
    public CompletableFuture<SoundEndReason> ended() {
        return endedFuture;
    }

    @Override
    public String toString() {
        return "SoundHandle[" + id + ", " + state + ", " + request.source() + "]";
    }
}
