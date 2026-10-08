package dev.valkdz.cdiscapi.command;

import dev.valkdz.cdisc.Main;
import dev.valkdz.cdisc.api.GuiControl;
import dev.valkdz.cdisc.api.NowPlaying;
import dev.valkdz.cdisc.api.PlayerSettings;
import dev.valkdz.cdisc.api.QueueControl;
import dev.valkdz.cdisc.api.QueueEntry;
import dev.valkdz.cdisc.api.QueueException;
import dev.valkdz.cdisc.feature.lyrics.LyricsMode;
import dev.valkdz.cdisc.jukebox.queue.DiscQueue;
import dev.valkdz.cdisc.util.Tasks;
import dev.valkdz.cdisc.util.TimeUtils;
import dev.valkdz.cdiscapi.CDiscApiPlugin;
import dev.valkdz.cdiscapi.api.CDiscAPI;
import dev.valkdz.cdiscapi.api.JukeboxControl;
import dev.valkdz.cdiscapi.api.SoundFailedException;
import dev.valkdz.cdiscapi.api.SoundHandle;
import dev.valkdz.cdiscapi.api.SoundRefusedException;
import dev.valkdz.cdiscapi.api.SoundRequest;
import dev.valkdz.cdiscapi.core.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ApiCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("help", "playsound", "stop", "pause", "resume",
            "volume", "distance", "seek", "loop", "move", "list", "info", "jukebox", "jukeboxes", "local", "queue",
            "gui", "disc", "prefs", "reload");
    private static final Set<String> OPTION_KEYS = Set.of("id", "at", "target", "volume", "distance", "loop", "start",
            "public", "title", "author");
    private static final List<String> OPTIONS = List.of("id=", "at=", "target=", "volume=", "distance=", "loop=true",
            "start=", "public=true", "title=", "author=");
    private static final List<String> SELECTORS = List.of("@a", "@p", "@r", "@s");
    private static final List<String> SOURCE_PREFIXES = List.of("local:", "yt:", "sc:", "sp:", "ym:", "vk:",
            "https://");
    private static final List<String> JUKEBOX_ACTIONS = List.of("play", "stop", "pause", "resume", "skip", "prev",
            "seek", "repeat", "shuffle", "volume", "info");
    private static final List<String> QUEUE_ACTIONS = List.of("list", "add", "remove", "move", "clear", "play",
            "after", "crossfade");
    private static final List<String> SCREENS = List.of("player", "advanced", "queue", "speakers", "broadcast",
            "lyrics", "settings", "playlist", "close");
    private static final List<String> PREFS = List.of("lyrics", "messages", "scoreboard");
    private static final int COMPLETION_LIMIT = 40;
    private static final int LIST_LIMIT = 50;

    private final CDiscApiPlugin plugin;
    private final CDiscAPI api;

    public ApiCommand(CDiscApiPlugin plugin, CDiscAPI api) {
        this.plugin = plugin;
        this.api = api;
    }

    private static final class Fail extends RuntimeException {
        Fail(String message) {
            super(message, null, false, false);
        }
    }

    private Messages msg() {
        return plugin.messages();
    }

    private static String permission(String sub) {
        return switch (sub) {
            case "playsound" -> "cdisc.api.playsound";
            case "stop", "pause", "resume", "volume", "distance", "seek", "loop", "move" -> "cdisc.api.control";
            case "list", "info", "jukeboxes", "local" -> "cdisc.api.list";
            case "jukebox" -> "cdisc.api.jukebox";
            case "queue" -> "cdisc.api.queue";
            case "gui" -> "cdisc.api.gui";
            case "disc" -> "cdisc.api.disc";
            case "prefs" -> "cdisc.api.prefs";
            case "reload" -> "cdisc.api.reload";
            default -> null;
        };
    }

    private static boolean allowed(CommandSender sender, String sub) {
        String node = permission(sub);
        return node == null || sender.hasPermission(node);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] raw) {
        List<String> args = Args.tokenize(raw);
        String sub = args.isEmpty() ? "help" : args.get(0).toLowerCase(Locale.ROOT);
        if (!SUBCOMMANDS.contains(sub)) sub = "help";
        if (!allowed(sender, sub)) {
            sender.sendMessage(msg().get("no-permission"));
            return true;
        }

        try {
            switch (sub) {
                case "playsound" -> playsound(sender, args);
                case "stop" -> stop(sender, args);
                case "pause" -> {
                    handle(args, 1, "pause <id>").pause();
                    sender.sendMessage(msg().get("done"));
                }
                case "resume" -> {
                    handle(args, 1, "resume <id>").resume();
                    sender.sendMessage(msg().get("done"));
                }
                case "volume" -> {
                    SoundHandle sound = handle(args, 2, "volume <id> <0-200>");
                    sound.volume(integer(args.get(2)));
                    sender.sendMessage(msg().get("done"));
                }
                case "distance" -> {
                    SoundHandle sound = handle(args, 2, "distance <id> <blocks>");
                    sound.distance((float) number(args.get(2)));
                    sender.sendMessage(msg().get("done"));
                }
                case "seek" -> {
                    SoundHandle sound = handle(args, 2, "seek <id> <1:30|+10|-10>");
                    sound.seek(target(sound.position(), args.get(2)));
                    sender.sendMessage(msg().get("done"));
                }
                case "loop" -> {
                    SoundHandle sound = handle(args, 2, "loop <id> <true|false>");
                    sound.loop(bool(args.get(2)));
                    sender.sendMessage(msg().get("done"));
                }
                case "move" -> move(sender, args);
                case "list" -> list(sender);
                case "info" -> info(sender, handle(args, 1, "info <id>"));
                case "jukebox" -> jukebox(sender, args);
                case "jukeboxes" -> jukeboxes(sender);
                case "local" -> local(sender, args);
                case "queue" -> queue(sender, args);
                case "gui" -> gui(sender, args);
                case "disc" -> disc(sender, args);
                case "prefs" -> prefs(sender, args);
                case "reload" -> {
                    if (args.size() > 1 && args.get(1).equalsIgnoreCase("cdisc")) {
                        api.reloadCDisc();
                        sender.sendMessage(msg().get("cdisc-reloaded"));
                        return true;
                    }
                    plugin.reloadSettings();
                    sender.sendMessage(msg().get("reloaded"));
                }
                default -> help(sender);
            }
        } catch (Fail e) {
            sender.sendMessage(e.getMessage());
        } catch (SoundRefusedException e) {
            sender.sendMessage(refusal(e));
        } catch (QueueException e) {
            sender.sendMessage(msg().get("refused", e.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            sender.sendMessage(msg().get("refused", e.getMessage()));
        }
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(msg().plain("&fCDisc-API &7" + plugin.getDescription().getVersion()));
        String[][] lines = {
                {"playsound", "playsound <source> [player|@a] [target=<selector>] [id=] [at=x,y,z,world] [volume=] [distance=] "
                        + "[loop=true] [start=1:30] [public=true] [title=] [author=]"},
                {"stop", "stop <id|id*|player|all>"},
                {"pause", "pause <id>  &8|  &7resume <id>"},
                {"volume", "volume <id> <0-200>  &8|  &7distance <id> <blocks>"},
                {"seek", "seek <id> <1:30|+10|-10>  &8|  &7loop <id> <true|false>"},
                {"move", "move <id> <player|x,y,z,world|here>"},
                {"list", "list  &8|  &7info <id>  &8|  &7jukeboxes  &8|  &7local [filter]"},
                {"jukebox", "jukebox <look|x,y,z,world> <play [source]|stop|pause|resume|skip|prev|seek <t>"
                        + "|repeat <off|queue|track>|shuffle <on|off>|volume <0-100>|info>"},
                {"queue", "queue <look|x,y,z,world> <list|add <source>|remove <slot>|move <from> <to>|clear"
                        + "|play <slot>|after <keep|eject|move_to_end>|crossfade <on|off>>"},
                {"gui", "gui <player|@a> <player|advanced|queue|speakers|broadcast> [look|x,y,z,world]"},
                {"gui", "gui <player|@a> <lyrics|settings|close>  &8|  &7gui <player> playlist <source>"},
                {"disc", "disc <player> <source>"},
                {"prefs", "prefs <player> <lyrics <mode>|messages <on|off>|scoreboard <on|off>>"},
                {"reload", "reload [cdisc]"},
        };
        for (String[] line : lines) {
            if (allowed(sender, line[0])) sender.sendMessage(msg().plain("&d/cdisc-api &7" + line[1]));
        }
    }

    private void playsound(CommandSender sender, List<String> args) {
        if (args.size() < 2) throw new Fail(msg().get("usage", "playsound <source> [player] [options]"));
        String source = args.get(1);

        String targetToken = null;
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 2; i < args.size(); i++) {
            String token = args.get(i);
            int eq = token.indexOf('=');
            String key = eq > 0 ? token.substring(0, eq).toLowerCase(Locale.ROOT) : null;
            if (key != null && OPTION_KEYS.contains(key)) {
                options.put(key, token.substring(eq + 1));
            } else if (targetToken == null) {
                targetToken = token;
            } else {
                throw new Fail(msg().get("unexpected", token));
            }
        }

        Location at = options.containsKey("at") ? location(sender, options.get("at")) : null;
        boolean everyone = options.containsKey("public") && bool(options.get("public"));
        String hearers = options.get("target");
        if (hearers != null && targetToken == null) {
            targetToken = hearers;
            hearers = null;
        }
        if (hearers != null && (at != null || everyone)) {
            throw new Fail(msg().get("target-conflict"));
        }
        List<Entity> targets;
        if (targetToken != null) {
            targets = entities(sender, targetToken);
        } else if (at != null) {
            targets = List.of();
        } else if (sender instanceof Player self) {
            targets = List.of(self);
        } else {
            throw new Fail(msg().get("needs-target"));
        }

        Integer volume = options.containsKey("volume") ? integer(options.get("volume")) : null;
        Float distance = options.containsKey("distance") ? (float) number(options.get("distance")) : null;
        boolean loop = options.containsKey("loop") && bool(options.get("loop"));
        Long start = options.containsKey("start") ? time(options.get("start")) : null;
        String baseId = options.get("id");

        Supplier<SoundRequest.Builder> base = () -> {
            SoundRequest.Builder b = SoundRequest.builder(source).owner(plugin).loop(loop)
                    .title(options.get("title")).author(options.get("author"));
            if (volume != null) b.volume(volume);
            if (distance != null) b.distance(distance);
            if (start != null) b.startAt(start);
            return b;
        };

        List<SoundRequest> requests = new ArrayList<>();
        if (hearers != null) {
            List<Player> listeners = players(sender, hearers);
            for (Entity anchor : targets) {
                for (Player listener : listeners) {
                    SoundRequest.Builder b = base.get().following(anchor).listener(listener);
                    if (targets.size() == 1 && listeners.size() == 1) {
                        b.id(baseId);
                    } else if (baseId != null) {
                        b.id(baseId + (targets.size() > 1 ? "-" + anchor.getName() : "") + "-" + listener.getName());
                    }
                    requests.add(b.build());
                }
            }
            targets = List.of();
        } else if (targets.isEmpty()) {
            requests.add(base.get().id(baseId).at(at).build());
        }
        for (Entity target : targets) {
            SoundRequest.Builder b = base.get();
            if (at != null) b.at(at);
            else b.following(target);
            if (!everyone && target instanceof Player listener) b.listener(listener);
            if (targets.size() == 1) b.id(baseId);
            else if (baseId != null) b.id(baseId + "-" + target.getName());
            requests.add(b.build());
        }

        if (requests.size() == 1) {
            SoundHandle sound = api.play(requests.get(0));
            sender.sendMessage(msg().get("loading", source, sound.id()));
            report(sender, sound);
            return;
        }
        int played = 0;
        for (SoundRequest request : requests) {
            try {
                api.play(request);
                played++;
            } catch (SoundRefusedException e) {
                sender.sendMessage(refusal(e));
                break;
            }
        }
        sender.sendMessage(msg().get("loading-many", source, played));
    }

    private void report(CommandSender sender, SoundHandle sound) {
        sound.started().whenComplete((started, error) -> {
            if (error == null) {
                reply(sender, msg().get("started", started.title(), started.id()));
                return;
            }
            Throwable cause = error instanceof CompletionException && error.getCause() != null
                    ? error.getCause() : error;
            String reason = cause instanceof SoundFailedException failed
                    ? failed.reason().name().toLowerCase(Locale.ROOT).replace('_', ' ') : String.valueOf(cause);
            reply(sender, msg().get("failed", sound.id(), reason));
        });
    }

    private void reply(CommandSender sender, String text) {
        if (!plugin.isEnabled()) return;
        if (sender instanceof Player player) {
            if (player.isOnline()) Tasks.entity(plugin, player, () -> player.sendMessage(text));
        } else {
            Tasks.global(plugin, () -> sender.sendMessage(text));
        }
    }

    private String refusal(SoundRefusedException e) {
        return switch (e.reason()) {
            case NO_VOICE -> msg().get("no-voice");
            case LIMIT -> msg().get("limit", plugin.settings().maxSounds());
            default -> msg().get("refused", e.getMessage());
        };
    }

    private void stop(CommandSender sender, List<String> args) {
        if (args.size() < 2) throw new Fail(msg().get("usage", "stop <id|id*|player|all>"));
        String what = args.get(1);

        int stopped = 0;
        if (what.equalsIgnoreCase("all")) {
            stopped = api.stopAll();
        } else if (what.endsWith("*")) {
            String prefix = what.substring(0, what.length() - 1);
            for (SoundHandle sound : api.sounds()) {
                if (!sound.id().startsWith(prefix)) continue;
                sound.stop();
                stopped++;
            }
        } else {
            Optional<SoundHandle> exact = api.sound(what);
            if (exact.isPresent()) {
                exact.get().stop();
                stopped = 1;
            } else {
                Player player = Bukkit.getPlayerExact(what);
                if (player == null) throw new Fail(msg().get("unknown-sound", what));
                for (SoundHandle sound : api.sounds()) {
                    boolean hears = player.getUniqueId().equals(sound.listener());
                    boolean carries = sound.following() != null
                            && player.getUniqueId().equals(sound.following().getUniqueId());
                    if (!hears && !carries) continue;
                    sound.stop();
                    stopped++;
                }
            }
        }
        sender.sendMessage(msg().get("stopped", stopped));
    }

    private void move(CommandSender sender, List<String> args) {
        SoundHandle sound = handle(args, 2, "move <id> <player|x,y,z,world|here>");
        String where = args.get(2);
        if (where.equalsIgnoreCase("here") || where.indexOf(',') >= 0) {
            sound.moveTo(location(sender, where));
        } else {
            sound.follow(entities(sender, where).get(0));
        }
        sender.sendMessage(msg().get("done"));
    }

    private void list(CommandSender sender) {
        List<SoundHandle> sounds = new ArrayList<>(api.sounds());
        if (sounds.isEmpty()) {
            sender.sendMessage(msg().get("none"));
            return;
        }
        sounds.sort((a, b) -> a.id().compareToIgnoreCase(b.id()));
        sender.sendMessage(msg().plain("&f" + sounds.size() + " &7sound(s):"));
        for (SoundHandle sound : sounds.subList(0, Math.min(LIST_LIMIT, sounds.size()))) {
            sender.sendMessage(msg().plain("&f" + sound.id() + " &8| &7" + state(sound) + " &8| &f"
                    + sound.title() + " &8| &7" + progress(sound) + " &8| &7" + where(sound)));
        }
    }

    private void info(CommandSender sender, SoundHandle sound) {
        Player listener = sound.listener() == null ? null : Bukkit.getPlayer(sound.listener());
        String[] lines = {
                "&fid: &7" + sound.id() + " &8(" + state(sound) + ")",
                "&fsource: &7" + sound.source(),
                "&ftrack: &7" + sound.title() + (sound.author() == null ? "" : " &8- &7" + sound.author()),
                "&furi: &7" + (sound.uri() == null ? "-" : sound.uri()),
                "&fprogress: &7" + progress(sound),
                "&fwhere: &7" + where(sound),
                "&fheard by: &7" + (sound.listener() == null ? "everyone nearby"
                        : listener != null ? listener.getName() : sound.listener().toString()),
                "&fvolume: &7" + sound.volume() + " &8| &fdistance: &7" + Math.round(sound.distance())
                        + " &8| &floop: &7" + sound.looping(),
                "&fowner: &7" + (sound.owner() == null ? "-" : sound.owner().getName()),
        };
        for (String line : lines) sender.sendMessage(msg().plain(line));
    }

    private void jukebox(CommandSender sender, List<String> args) {
        if (args.size() < 3) throw new Fail(msg().get("usage", "jukebox <look|x,y,z,world> <action>"));
        Block block = jukeboxBlock(sender, args.get(1));
        JukeboxControl jukebox = api.jukebox(block);
        String action = args.get(2).toLowerCase(Locale.ROOT);

        switch (action) {
            case "play" -> {
                if (args.size() > 3) {
                    jukebox.play(Args.join(args, 3));
                } else {
                    jukebox.resumeQueue();
                }
            }
            case "stop" -> jukebox.stop();
            case "pause" -> jukebox.pause();
            case "resume" -> jukebox.resume();
            case "skip", "next" -> jukebox.skip();
            case "prev", "previous" -> jukebox.previous();
            case "seek" -> {
                if (args.size() < 4) throw new Fail(msg().get("usage", "jukebox <where> seek <1:30|+10|-10>"));
                long now = jukebox.nowPlaying().map(NowPlaying::positionMs).orElse(0L);
                jukebox.seek(target(now, args.get(3)));
            }
            case "repeat" -> {
                if (args.size() < 4) throw new Fail(msg().get("usage", "jukebox <where> repeat <off|queue|track>"));
                try {
                    jukebox.repeat(JukeboxControl.Repeat.valueOf(args.get(3).toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    throw new Fail(msg().get("usage", "jukebox <where> repeat <off|queue|track>"));
                }
            }
            case "shuffle" -> {
                if (args.size() < 4) throw new Fail(msg().get("usage", "jukebox <where> shuffle <on|off>"));
                jukebox.shuffle(bool(args.get(3)));
            }
            case "volume" -> {
                if (args.size() < 4) throw new Fail(msg().get("usage", "jukebox <where> volume <0-100>"));
                jukebox.volume(integer(args.get(3)));
            }
            case "info" -> {
                jukeboxInfo(sender, jukebox);
                return;
            }
            default -> throw new Fail(msg().get("unexpected", action));
        }
        sender.sendMessage(msg().get("done"));
    }

    private void jukeboxInfo(CommandSender sender, JukeboxControl jukebox) {
        Block block = jukebox.block();
        sender.sendMessage(msg().plain("&fjukebox: &7" + block.getWorld().getName() + " "
                + block.getX() + "," + block.getY() + "," + block.getZ()));
        Optional<NowPlaying> now = jukebox.nowPlaying();
        if (now.isEmpty()) {
            sender.sendMessage(msg().get("none"));
        } else {
            NowPlaying np = now.get();
            sender.sendMessage(msg().plain("&ftrack: &7" + np.title() + " &8- &7" + np.author()));
            sender.sendMessage(msg().plain("&fprogress: &7" + (np.live() ? "LIVE"
                    : TimeUtils.format(np.positionMs()) + " / " + TimeUtils.format(np.durationMs()))
                    + (np.paused() ? " &8(paused)" : "")));
        }
        sender.sendMessage(msg().plain("&frepeat: &7" + jukebox.repeat().name().toLowerCase(Locale.ROOT)
                + " &8| &fshuffle: &7" + jukebox.shuffle() + " &8| &fqueue: &7" + jukebox.queueSize()
                + " &8| &fvolume: &7" + jukebox.volume()));
    }

    private void jukeboxes(CommandSender sender) {
        List<Block> blocks = new ArrayList<>(api.playingJukeboxes());
        if (blocks.isEmpty()) {
            sender.sendMessage(msg().get("none"));
            return;
        }
        for (Block block : blocks.subList(0, Math.min(LIST_LIMIT, blocks.size()))) {
            String title = dev.valkdz.cdisc.api.CDiscApi.nowPlaying(block).map(NowPlaying::title).orElse("-");
            sender.sendMessage(msg().plain("&7" + block.getWorld().getName() + " &f" + block.getX() + ","
                    + block.getY() + "," + block.getZ() + " &8| &f" + title));
        }
    }

    private void local(CommandSender sender, List<String> args) {
        String filter = Args.join(args, 1).toLowerCase(Locale.ROOT);
        List<String> tracks = new ArrayList<>();
        for (String track : api.localTracks()) {
            if (filter.isEmpty() || track.toLowerCase(Locale.ROOT).contains(filter)) tracks.add(track);
        }
        if (tracks.isEmpty()) {
            sender.sendMessage(msg().get("none"));
            return;
        }
        sender.sendMessage(msg().plain("&f" + tracks.size() + " &7local track(s):"));
        for (String track : tracks.subList(0, Math.min(LIST_LIMIT, tracks.size()))) {
            sender.sendMessage(msg().plain("&flocal:" + track));
        }
    }

    private void queue(CommandSender sender, List<String> args) {
        if (args.size() < 3) throw new Fail(msg().get("usage", "queue <look|x,y,z,world> <action>"));
        QueueControl queue = api.jukebox(jukeboxBlock(sender, args.get(1))).queue();
        String action = args.get(2).toLowerCase(Locale.ROOT);

        switch (action) {
            case "list" -> queueList(sender, queue);
            case "add" -> {
                if (args.size() < 4) throw new Fail(msg().get("usage", "queue <where> add <source>"));
                String source = Args.join(args, 3);
                queue.add(source).whenComplete((added, error) -> reply(sender, error == null
                        ? msg().get("queue-added", added.size(), source)
                        : msg().get("queue-failed", source, cause(error))));
            }
            case "remove" -> {
                int slot = slot(args, 3, "queue <where> remove <slot>");
                answer(sender, queue.remove(slot), removed -> removed
                        ? msg().get("queue-removed", slot + 1) : msg().get("queue-nothing", slot + 1));
            }
            case "move" -> {
                int from = slot(args, 3, "queue <where> move <from> <to>");
                int to = slot(args, 4, "queue <where> move <from> <to>");
                answer(sender, queue.move(from, to), moved -> moved
                        ? msg().get("done") : msg().get("queue-nothing", from + 1));
            }
            case "clear" -> answer(sender, queue.clear(), removed -> msg().get("queue-cleared", removed));
            case "play" -> {
                int slot = slot(args, 3, "queue <where> play <slot>");
                if (queue.entry(slot).isEmpty()) throw new Fail(msg().get("queue-nothing", slot + 1));
                queue.play(slot);
                sender.sendMessage(msg().get("done"));
            }
            case "after" -> {
                if (args.size() < 4) throw new Fail(msg().get("usage", "queue <where> after <keep|eject|move_to_end>"));
                try {
                    queue.afterPlay(QueueControl.AfterPlay.valueOf(args.get(3).toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    throw new Fail(msg().get("usage", "queue <where> after <keep|eject|move_to_end>"));
                }
                sender.sendMessage(msg().get("done"));
            }
            case "crossfade" -> {
                if (args.size() < 4) throw new Fail(msg().get("usage", "queue <where> crossfade <on|off>"));
                queue.crossfade(bool(args.get(3)));
                sender.sendMessage(msg().get("done"));
            }
            default -> throw new Fail(msg().get("unexpected", action));
        }
    }

    private void queueList(CommandSender sender, QueueControl queue) {
        List<QueueEntry> entries = queue.entries();
        if (entries.isEmpty()) {
            sender.sendMessage(msg().get("queue-empty"));
            return;
        }
        sender.sendMessage(msg().plain("&f" + entries.size() + "&7/" + queue.capacity() + " &8| &fafter: &7"
                + queue.afterPlay().name().toLowerCase(Locale.ROOT) + " &8| &fcrossfade: &7" + queue.crossfade()
                + " &8| &freal discs: &7" + queue.addsRealDiscs()));
        for (QueueEntry entry : entries) {
            String length = entry.live() ? "LIVE" : entry.lengthMs() > 0 ? TimeUtils.format(entry.lengthMs()) : "-";
            sender.sendMessage(msg().plain((entry.current() ? "&a> " : "&8  ") + "&f" + (entry.slot() + 1)
                    + " &8| &f" + entry.title() + (entry.author() == null ? "" : " &8- &7" + entry.author())
                    + " &8| &7" + length + (entry.locked() ? " &8(locked)" : "")));
        }
    }

    private int slot(List<String> args, int index, String usage) {
        if (args.size() <= index) throw new Fail(msg().get("usage", usage));
        int slot = integer(args.get(index));
        if (slot < 1 || slot > DiscQueue.CAPACITY) {
            throw new Fail(msg().get("bad-number", args.get(index)));
        }
        return slot - 1;
    }

    private <T> void answer(CommandSender sender, CompletableFuture<T> future,
                            Function<T, String> text) {
        future.whenComplete((value, error) -> reply(sender, error == null
                ? text.apply(value) : msg().get("refused", cause(error))));
    }

    private static String cause(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private void gui(CommandSender sender, List<String> args) {
        if (args.size() < 3) throw new Fail(msg().get("usage", "gui <player|@a> <screen> [look|x,y,z,world]"));
        List<Player> players = players(sender, args.get(1));
        String screen = args.get(2).toLowerCase(Locale.ROOT);
        GuiControl gui = api.gui();

        Consumer<Player> action = switch (screen) {
            case "close" -> gui::close;
            case "playlist" -> {
                if (args.size() < 4) throw new Fail(msg().get("usage", "gui <player> playlist <source>"));
                String source = Args.join(args, 3);
                yield player -> gui.openPlaylist(player, source);
            }
            case "lyrics" -> player -> gui.open(player, GuiControl.Screen.LYRICS_LOOK);
            case "settings" -> player -> gui.open(player, GuiControl.Screen.SETTINGS);
            case "player", "advanced", "queue", "speakers", "broadcast" -> {
                GuiControl.Screen chosen = GuiControl.Screen.valueOf(screen.toUpperCase(Locale.ROOT));
                Block block = jukeboxBlock(sender, args.size() > 3 ? args.get(3) : "look");
                yield player -> gui.open(player, chosen, block);
            }
            default -> throw new Fail(msg().get("unexpected", screen));
        };
        for (Player player : players) action.accept(player);
        sender.sendMessage(msg().get("gui-opened", screen, players.size()));
    }

    private void disc(CommandSender sender, List<String> args) {
        if (args.size() < 3) throw new Fail(msg().get("usage", "disc <player> <source>"));
        List<Player> players = players(sender, args.get(1));
        String source = Args.join(args, 2);
        api.createDisc(source).whenComplete((disc, error) -> {
            if (error != null) {
                reply(sender, msg().get("disc-failed", source, cause(error)));
                return;
            }
            for (Player player : players) {
                Tasks.entity(plugin, player, () -> {
                    if (!player.isOnline()) return;
                    for (ItemStack left : player.getInventory().addItem(disc.clone()).values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), left);
                    }
                });
                reply(sender, msg().get("disc-given", player.getName(), source));
            }
        });
    }

    private void prefs(CommandSender sender, List<String> args) {
        if (args.size() < 3) throw new Fail(msg().get("usage", "prefs <player> <lyrics|messages|scoreboard> [value]"));
        List<Player> players = players(sender, args.get(1));
        String what = args.get(2).toLowerCase(Locale.ROOT);
        if (!PREFS.contains(what)) throw new Fail(msg().get("unexpected", what));

        if (args.size() < 4) {
            for (Player player : players) {
                PlayerSettings settings = api.player(player);
                String value = switch (what) {
                    case "lyrics" -> settings.lyrics() + " &8(" + String.join(", ", settings.lyricsModes()) + ")";
                    case "messages" -> String.valueOf(settings.trackMessages());
                    default -> String.valueOf(settings.lyricsScoreboard());
                };
                sender.sendMessage(msg().plain("&f" + player.getName() + " &8| &f" + what + ": &7" + value));
            }
            return;
        }
        String value = args.get(3);
        for (Player player : players) {
            PlayerSettings settings = api.player(player);
            switch (what) {
                case "lyrics" -> settings.lyrics(value);
                case "messages" -> settings.trackMessages(bool(value));
                default -> settings.lyricsScoreboard(bool(value));
            }
        }
        sender.sendMessage(msg().get("done"));
    }

    private List<Player> players(CommandSender sender, String token) {
        List<Player> out = new ArrayList<>();
        for (Entity entity : entities(sender, token)) {
            if (entity instanceof Player player) out.add(player);
        }
        if (out.isEmpty()) throw new Fail(msg().get("unknown-player", token));
        return out;
    }

    private SoundHandle handle(List<String> args, int need, String usage) {
        if (args.size() <= need) throw new Fail(msg().get("usage", usage));
        String id = args.get(1);
        return api.sound(id).orElseThrow(() -> new Fail(msg().get("unknown-sound", id)));
    }

    private Block jukeboxBlock(CommandSender sender, String where) {
        Block block;
        if (where.equalsIgnoreCase("look") || where.equalsIgnoreCase("here")) {
            if (!(sender instanceof Player player)) throw new Fail(msg().get("needs-target"));
            block = player.getTargetBlockExact(8);
        } else {
            block = location(sender, where).getBlock();
        }
        if (block == null || block.getType() != Material.JUKEBOX) throw new Fail(msg().get("not-jukebox", where));
        return block;
    }

    private List<Entity> entities(CommandSender sender, String token) {
        List<Entity> found = new ArrayList<>();
        if (token.startsWith("@")) {
            try {
                found.addAll(Bukkit.selectEntities(sender, token));
            } catch (RuntimeException e) {
                throw new Fail(msg().get("unknown-player", token));
            }
        } else {
            Player player = Bukkit.getPlayerExact(token);
            if (player == null) player = Bukkit.getPlayer(token);
            if (player != null) found.add(player);
        }
        if (found.isEmpty()) throw new Fail(msg().get("unknown-player", token));
        return found;
    }

    private Location location(CommandSender sender, String raw) {
        Location base = senderLocation(sender);
        if (raw.equalsIgnoreCase("here")) {
            if (base == null) throw new Fail(msg().get("needs-target"));
            return base;
        }
        String[] parts = raw.split(",");
        if (parts.length < 3 || parts.length > 4) throw new Fail(msg().get("bad-location", raw));

        World world;
        if (parts.length == 4) {
            world = Bukkit.getWorld(parts[3].trim());
        } else if (base != null) {
            world = base.getWorld();
        } else {
            world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        }
        if (world == null) throw new Fail(msg().get("bad-location", raw));

        double x = coordinate(parts[0], base == null ? null : base.getX(), raw);
        double y = coordinate(parts[1], base == null ? null : base.getY(), raw);
        double z = coordinate(parts[2], base == null ? null : base.getZ(), raw);
        return new Location(world, x, y, z);
    }

    private double coordinate(String part, Double relativeTo, String raw) {
        String text = part.trim();
        try {
            if (text.startsWith("~")) {
                if (relativeTo == null) throw new Fail(msg().get("bad-location", raw));
                return relativeTo + (text.length() == 1 ? 0.0 : Double.parseDouble(text.substring(1)));
            }
            double value = Double.parseDouble(text);
            return text.contains(".") ? value : value + 0.5;
        } catch (NumberFormatException e) {
            throw new Fail(msg().get("bad-location", raw));
        }
    }

    private static Location senderLocation(CommandSender sender) {
        if (sender instanceof Entity entity) return entity.getLocation();
        if (sender instanceof BlockCommandSender block) return block.getBlock().getLocation().add(0.5, 0.5, 0.5);
        return null;
    }

    private int integer(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new Fail(msg().get("bad-number", raw));
        }
    }

    private double number(String raw) {
        try {
            double value = Double.parseDouble(raw.trim());
            if (Double.isNaN(value) || Double.isInfinite(value)) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException e) {
            throw new Fail(msg().get("bad-number", raw));
        }
    }

    private static boolean bool(String raw) {
        String text = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return text.equals("true") || text.equals("on") || text.equals("yes") || text.equals("1");
    }

    private long time(String raw) {
        Long parsed = TimeUtils.parseTimecode(raw);
        if (parsed == null) throw new Fail(msg().get("bad-time", raw));
        return parsed;
    }

    private long target(long current, String raw) {
        String text = raw.trim();
        if (text.startsWith("+")) return current + time(text.substring(1));
        if (text.startsWith("-")) return Math.max(0L, current - time(text.substring(1)));
        return time(text);
    }

    private static String state(SoundHandle sound) {
        return sound.state().name().toLowerCase(Locale.ROOT);
    }

    private static String progress(SoundHandle sound) {
        if (sound.state() == SoundHandle.State.LOADING) return "-";
        if (sound.live()) return "LIVE";
        long duration = sound.duration();
        return TimeUtils.format(sound.position()) + (duration > 0 ? " / " + TimeUtils.format(duration) : "");
    }

    private static String where(SoundHandle sound) {
        Entity following = sound.following();
        if (following != null) return "follows " + following.getName();
        Location at = sound.location();
        if (at == null || at.getWorld() == null) return "-";
        return at.getWorld().getName() + " " + at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            for (String sub : SUBCOMMANDS) if (allowed(sender, sub)) subs.add(sub);
            return matching(subs, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!SUBCOMMANDS.contains(sub) || !allowed(sender, sub)) return List.of();
        String typed = args[args.length - 1];
        int at = args.length - 1;

        switch (sub) {
            case "playsound" -> {
                if (at == 1) return sources(sender, typed);
                if (typed.toLowerCase(Locale.ROOT).startsWith("at=")) return matching(coordinates(sender, "at="), typed);
                if (typed.toLowerCase(Locale.ROOT).startsWith("target=")) {
                    List<String> out = new ArrayList<>();
                    for (String target : targets()) out.add("target=" + target);
                    return matching(out, typed);
                }
                List<String> out = new ArrayList<>(OPTIONS);
                boolean targetGiven = false;
                for (int i = 2; i < args.length - 1; i++) if (args[i].indexOf('=') < 0) targetGiven = true;
                if (!targetGiven) out.addAll(targets());
                return matching(out, typed);
            }
            case "stop" -> {
                if (at != 1) return List.of();
                List<String> out = ids();
                out.add("all");
                out.addAll(players());
                return matching(out, typed);
            }
            case "pause", "resume", "info" -> {
                return at == 1 ? matching(ids(), typed) : List.of();
            }
            case "volume", "distance", "seek", "loop", "move" -> {
                if (at == 1) return matching(ids(), typed);
                if (at != 2) return List.of();
                return matching(switch (sub) {
                    case "volume" -> List.of("25", "50", "100", "150");
                    case "distance" -> List.of("16", "32", "48", "64");
                    case "seek" -> List.of("+10", "-10", "0:00", "1:00");
                    case "loop" -> List.of("true", "false");
                    default -> {
                        List<String> out = new ArrayList<>(players());
                        out.add("here");
                        out.addAll(coordinates(sender, ""));
                        yield out;
                    }
                }, typed);
            }
            case "jukebox" -> {
                if (at == 1) return matching(jukeboxSpots(sender), typed);
                if (at == 2) return matching(JUKEBOX_ACTIONS, typed);
                if (at == 3) {
                    return switch (args[2].toLowerCase(Locale.ROOT)) {
                        case "play" -> sources(sender, typed);
                        case "repeat" -> matching(List.of("off", "queue", "track"), typed);
                        case "shuffle" -> matching(List.of("on", "off"), typed);
                        case "seek" -> matching(List.of("+10", "-10", "0:00"), typed);
                        case "volume" -> matching(List.of("25", "50", "75", "100"), typed);
                        default -> List.of();
                    };
                }
                return List.of();
            }
            case "queue" -> {
                if (at == 1) return matching(jukeboxSpots(sender), typed);
                if (at == 2) return matching(QUEUE_ACTIONS, typed);
                if (at == 3) {
                    return switch (args[2].toLowerCase(Locale.ROOT)) {
                        case "add" -> sources(sender, typed);
                        case "after" -> matching(List.of("keep", "eject", "move_to_end"), typed);
                        case "crossfade" -> matching(List.of("on", "off"), typed);
                        case "remove", "move", "play" -> matching(List.of("1", "2", "3"), typed);
                        default -> List.of();
                    };
                }
                return List.of();
            }
            case "gui", "disc", "prefs" -> {
                if (at == 1) return matching(targets(), typed);
                if (sub.equals("disc")) return at == 2 ? sources(sender, typed) : List.of();
                if (sub.equals("gui")) {
                    if (at == 2) return matching(SCREENS, typed);
                    if (at == 3 && args[2].equalsIgnoreCase("playlist")) return sources(sender, typed);
                    if (at == 3 && !List.of("lyrics", "settings", "close").contains(args[2].toLowerCase(Locale.ROOT))) {
                        return matching(jukeboxSpots(sender), typed);
                    }
                    return List.of();
                }
                if (at == 2) return matching(PREFS, typed);
                if (at == 3) {
                    return args[2].equalsIgnoreCase("lyrics")
                            ? matching(lyricsModes(), typed) : matching(List.of("on", "off"), typed);
                }
                return List.of();
            }
            case "reload" -> {
                return at == 1 ? matching(List.of("cdisc"), typed) : List.of();
            }
            default -> {
                return List.of();
            }
        }
    }

    private static List<String> lyricsModes() {
        List<String> out = new ArrayList<>();
        for (LyricsMode mode : LyricsMode.values()) {
            out.add(mode.key());
        }
        return out;
    }

    private static List<String> jukeboxSpots(CommandSender sender) {
        List<String> out = new ArrayList<>(List.of("look"));
        if (sender instanceof Player player) {
            Block block = player.getTargetBlockExact(8);
            if (block != null && block.getType() == Material.JUKEBOX) {
                out.add(block.getX() + "," + block.getY() + "," + block.getZ() + "," + block.getWorld().getName());
            }
        }
        return out;
    }

    private List<String> sources(CommandSender sender, String typed) {
        Main cdisc = Main.getInstance();
        if (typed.toLowerCase(Locale.ROOT).startsWith("local:") && cdisc != null) {
            return cdisc.getLocalMusic().complete(typed, COMPLETION_LIMIT, sender);
        }
        return matching(SOURCE_PREFIXES, typed);
    }

    private List<String> ids() {
        List<String> out = new ArrayList<>();
        for (SoundHandle sound : api.sounds()) out.add(sound.id());
        return out;
    }

    private static List<String> players() {
        List<String> out = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) out.add(player.getName());
        return out;
    }

    private static List<String> targets() {
        List<String> out = new ArrayList<>(SELECTORS);
        out.addAll(players());
        return out;
    }

    private static List<String> coordinates(CommandSender sender, String prefix) {
        Location base = senderLocation(sender);
        if (base == null || base.getWorld() == null) return List.of(prefix + "x,y,z,world");
        return List.of(prefix + "~,~,~", prefix + base.getBlockX() + "," + base.getBlockY() + ","
                + base.getBlockZ() + "," + base.getWorld().getName());
    }

    private static List<String> matching(List<String> options, String typed) {
        String needle = typed.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(needle)) out.add(option);
            if (out.size() >= COMPLETION_LIMIT) break;
        }
        return out;
    }
}
