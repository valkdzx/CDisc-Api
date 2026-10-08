package dev.valkdz.cdiscapi.core;

import dev.valkdz.cdisc.Main;
import dev.valkdz.cdisc.api.CDiscApi;
import dev.valkdz.cdisc.api.DiscInfo;
import dev.valkdz.cdisc.api.GuiControl;
import dev.valkdz.cdisc.api.PlayerSettings;
import dev.valkdz.cdisc.jukebox.PlaybackManager;
import dev.valkdz.cdisc.util.Tasks;
import dev.valkdz.cdiscapi.CDiscApiPlugin;
import dev.valkdz.cdiscapi.api.CDiscAPI;
import dev.valkdz.cdiscapi.api.JukeboxControl;
import dev.valkdz.cdiscapi.api.SoundEndReason;
import dev.valkdz.cdiscapi.api.SoundHandle;
import dev.valkdz.cdiscapi.api.SoundRefusedException;
import dev.valkdz.cdiscapi.api.SoundRequest;
import dev.valkdz.cdiscapi.api.event.SoundPlayEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

public final class SoundService implements CDiscAPI, Listener {

    private final CDiscApiPlugin plugin;
    private final Map<String, ApiSound> sounds = new ConcurrentHashMap<>();
    private final AtomicInteger counter = new AtomicInteger();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "cdisc-api-worker");
        thread.setDaemon(true);
        return thread;
    });
    private Tasks.Handle ticker;

    public SoundService(CDiscApiPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        ticker = Tasks.globalTimer(plugin, this::tick, 1L, 1L);
    }

    public void shutdown() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        for (ApiSound sound : new ArrayList<>(sounds.values())) sound.finish(SoundEndReason.SHUTDOWN, null);
        worker.shutdown();
    }

    static PlaybackManager manager() {
        Main cdisc = Main.getInstance();
        return cdisc == null || !cdisc.isEnabled() ? null : cdisc.getAudioPlayerManager();
    }

    @Override
    public boolean isVoiceReady() {
        PlaybackManager apm = manager();
        return apm != null && apm.hasVoiceBackend();
    }

    @Override
    public SoundHandle play(SoundRequest request) {
        Objects.requireNonNull(request, "request");
        PlaybackManager apm = manager();
        if (apm == null) {
            throw new SoundRefusedException(SoundRefusedException.Reason.CDISC_DISABLED, "CDisc is not enabled");
        }
        if (!apm.hasVoiceBackend()) {
            throw new SoundRefusedException(SoundRefusedException.Reason.NO_VOICE, "No voice chat plugin is running");
        }

        String id = request.id() != null ? request.id() : nextId();
        SoundPlayEvent event = new SoundPlayEvent(id, request, !Bukkit.isPrimaryThread());
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            throw new SoundRefusedException(SoundRefusedException.Reason.CANCELLED, "Cancelled by a listener");
        }
        request = event.getRequest();

        Settings settings = plugin.settings();
        if (settings.maxSounds() > 0 && sounds.size() >= settings.maxSounds() && !sounds.containsKey(id)) {
            throw new SoundRefusedException(SoundRefusedException.Reason.LIMIT,
                    "Already " + settings.maxSounds() + " sounds playing");
        }

        boolean volumeGiven = request.volume() != SoundRequest.DEFAULT;
        int volume = clampVolume(volumeGiven ? request.volume() : defaultVolume());
        float distance = request.distance() > 0 ? clampDistance(request.distance()) : defaultDistance(apm);

        ApiSound sound = new ApiSound(this, plugin, id, request, volume, distance, volumeGiven);
        ApiSound previous = sounds.put(id, sound);
        if (previous != null) previous.finish(SoundEndReason.REPLACED, null);
        sound.begin();
        return sound;
    }

    private String nextId() {
        String id;
        do {
            id = "s" + counter.incrementAndGet();
        } while (sounds.containsKey(id));
        return id;
    }

    private int defaultVolume() {
        int configured = plugin.settings().defaultVolume();
        return configured >= 0 ? configured : Main.getInstance().cdiscConfig().getVolume();
    }

    private float defaultDistance(PlaybackManager apm) {
        float configured = plugin.settings().defaultDistance();
        return clampDistance(configured > 0 ? configured : apm.getBaseDistance());
    }

    int clampVolume(int volume) {
        return Math.max(0, Math.min(plugin.settings().maxVolume(), volume));
    }

    float clampDistance(float distance) {
        return Math.max(1f, Math.min(plugin.settings().maxDistance(), distance));
    }

    @Override
    public Optional<SoundHandle> sound(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(sounds.get(id));
    }

    @Override
    public Collection<SoundHandle> sounds() {
        return List.copyOf(sounds.values());
    }

    @Override
    public int stopAll() {
        int stopped = 0;
        for (ApiSound sound : new ArrayList<>(sounds.values())) {
            sound.stop();
            stopped++;
        }
        return stopped;
    }

    @Override
    public JukeboxControl jukebox(Block block) {
        if (block == null || block.getType() != Material.JUKEBOX) {
            throw new IllegalArgumentException("not a jukebox: " + block);
        }
        return new JukeboxController(block);
    }

    @Override
    public Collection<Block> playingJukeboxes() {
        return CDiscApi.playingJukeboxes();
    }

    @Override
    public List<String> localTracks() {
        Main cdisc = Main.getInstance();
        if (cdisc == null || !cdisc.getLocalMusic().isEnabled()) return List.of();
        return List.copyOf(cdisc.getLocalMusic().index());
    }

    @Override
    public GuiControl gui() {
        return CDiscApi.gui();
    }

    @Override
    public PlayerSettings player(Player player) {
        return CDiscApi.player(player);
    }

    @Override
    public CompletableFuture<ItemStack> createDisc(String source) {
        return createDisc(source, null, null);
    }

    @Override
    public CompletableFuture<ItemStack> createDisc(String source, String title, String author) {
        return CDiscApi.createDisc(source, title, author);
    }

    @Override
    public Optional<DiscInfo> readDisc(ItemStack item) {
        return CDiscApi.readDisc(item);
    }

    @Override
    public void reloadCDisc() {
        CDiscApi.reload();
    }

    void forget(ApiSound sound) {
        sounds.remove(sound.id(), sound);
    }

    void execute(Runnable work) {
        try {
            worker.execute(work);
        } catch (RejectedExecutionException e) {
            work.run();
        }
    }

    void fire(Event event) {
        if (Bukkit.isPrimaryThread()) {
            Bukkit.getPluginManager().callEvent(event);
        } else if (plugin.isEnabled()) {
            Tasks.global(plugin, () -> Bukkit.getPluginManager().callEvent(event));
        }
    }

    private void tick() {
        if (sounds.isEmpty()) return;
        long now = System.currentTimeMillis();
        long timeout = plugin.settings().loadTimeoutMs();

        for (ApiSound sound : sounds.values()) {
            if (sound.loadingTooLong(now, timeout)) {
                sound.finish(SoundEndReason.TIMED_OUT, null);
                continue;
            }
            Entity target = sound.following();
            if (target != null) Tasks.onEntity(plugin, target, sound::follow);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        UUID gone = e.getPlayer().getUniqueId();
        for (ApiSound sound : new ArrayList<>(sounds.values())) {
            if (sound.concerns(gone)) sound.finish(SoundEndReason.TARGET_GONE, null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent e) {
        Plugin going = e.getPlugin();
        boolean cdisc = going instanceof Main;
        for (ApiSound sound : new ArrayList<>(sounds.values())) {
            if (cdisc) {
                sound.finish(SoundEndReason.SHUTDOWN, null);
            } else if (going.equals(sound.owner()) && going != plugin) {
                sound.finish(SoundEndReason.STOPPED, null);
            }
        }
    }
}
