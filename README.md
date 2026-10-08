# CDisc-API

An add-on for CDisc: the `/cdisc-api` command and a
Java API that let admins, command blocks and other plugins play any CDisc source anywhere — not
only from a jukebox — and control CDisc jukeboxes remotely.

It does nothing on its own: it needs **CDisc** (2.2.1 or newer) and a voice chat plugin that CDisc
plays through (Simple Voice Chat or Plasmo Voice). Players hear it the same way they hear a CDisc
jukebox, through the voice chat mod.

## Install

1. Put `CDisc-API-<version>.jar` next to CDisc in `plugins/`.
2. Restart. The console says `Hooked into CDisc <version>.` If the CDisc version does not have
   something this build relies on, the add-on names it and turns itself off instead of failing later.

## Commands

Alias: `/cdapi`. Sources are whatever CDisc accepts: `local:file.mp3`, a URL, `yt:`, `sc:`, `sp:`,
`ym:`, `vk:` searches. Quote a source that has spaces: `"yt:never gonna give you up"`.

| Command | What it does |
|---|---|
| `/cdisc-api playsound <source> [player\|@a\|@p\|@r] [target=<selector>] [options]` | Play a sound |
| `/cdisc-api stop <id\|id*\|player\|all>` | Stop one sound, sounds by id prefix, a player's sounds, or everything |
| `/cdisc-api pause <id>` / `resume <id>` | Pause / resume |
| `/cdisc-api volume <id> <0-200>` | Change volume while playing |
| `/cdisc-api distance <id> <blocks>` | Change how far it carries |
| `/cdisc-api seek <id> <1:30\|+10\|-10>` | Jump to a time, or by seconds |
| `/cdisc-api loop <id> <true\|false>` | Repeat the track |
| `/cdisc-api move <id> <player\|x,y,z[,world]\|here>` | Move the sound, or make it follow someone |
| `/cdisc-api list` / `info <id>` | Running sounds |
| `/cdisc-api local [filter]` | Files in CDisc's local music folder |
| `/cdisc-api jukebox <look\|x,y,z[,world]> <action>` | Control a CDisc jukebox (below) |
| `/cdisc-api jukeboxes` | Jukeboxes playing right now |
| `/cdisc-api queue <look\|x,y,z[,world]> <action>` | Read and edit a jukebox's queue (below) |
| `/cdisc-api gui <player\|@a> <screen> [look\|x,y,z[,world]]` | Open a CDisc screen for players (below) |
| `/cdisc-api disc <player\|@a> <source>` | Give a real CDisc disc of that track |
| `/cdisc-api prefs <player\|@a> <setting> [value]` | Read or change a player's CDisc settings (below) |
| `/cdisc-api reload` / `reload cdisc` | Reload `config.yml` / reload CDisc itself |

### playsound

Like vanilla `/playsound`, the target is **who hears it**: each named player gets a private copy
that follows them. With no target a player plays it to themselves.

| Option | Meaning |
|---|---|
| `id=<name>` | Name for later commands. Reusing an id replaces that sound. Auto `s1`, `s2`... otherwise |
| `target=<selector>` | Who hears it: a player or any selector, `@a[distance=..20]` included. On its own it works like the target above. Next to a target, that target is what the sound follows and these players are who hear it, each their own copy |
| `at=x,y,z[,world]` | Play at a fixed spot (`~` works) instead of following the target |
| `public=true` | Everyone near the target or spot hears it, not only the target |
| `volume=<0-200>` | 100 = the track as it is |
| `distance=<blocks>` | How far it carries |
| `loop=true` | Start again when it ends |
| `start=<1:30>` | Start from this time |
| `title=` / `author=` | Shown name instead of the track's own |

```
/cdisc-api playsound local:intro.mp3
/cdisc-api playsound local:boss.ogg @a public=true loop=true id=boss
/cdisc-api playsound "yt:lofi radio" at=0,64,0,world public=true distance=48 id=plaza
/cdisc-api playsound local:roar.ogg @e[type=ender_dragon,limit=1] target=@a[distance=..100] id=roar
/cdisc-api volume plaza 60
/cdisc-api stop boss*
```

From the console or a command block, give either a player or `at=`.

### jukebox

`look` is the jukebox the player is looking at. Actions: `play [source]` (no source = resume its
queue), `stop`, `pause`, `resume`, `skip`, `prev`, `seek <t>`, `repeat <off|queue|track>`,
`shuffle <on|off>`, `volume <0-100>`, `info`.

### queue

Slots are numbered 1-42, as in the queue screen.

| Action | Does |
|---|---|
| `list` | Every queued track, the playing one marked |
| `add <source>` | Queue a track into the first free slot. A playlist link fills as many free slots as it can |
| `remove <slot>` | Take a track out. The playing one hands over to the next first |
| `move <from> <to>` | Swap two slots, or move into an empty one |
| `clear` | Empty the queue and stop |
| `play <slot>` | Play that slot now |
| `after <keep\|eject\|move_to_end>` | What happens to a track once it has played |
| `crossfade <on\|off>` | Crossfade between tracks of this jukebox |

```
/cdisc-api queue look add "yt:lofi hip hop"
/cdisc-api queue 0,64,0,world add https://www.youtube.com/playlist?list=PL...
/cdisc-api queue look move 5 1
/cdisc-api queue look list
```

**Queued tracks are locked discs.** They play like any disc but never exist as items: they
are not dropped, ejected, pulled by hoppers or taken from the queue screen, so filling queues
creates nothing a player could dupe. This is CDisc's own `api.allow-discs-in-queue` setting
in `plugins/CDisc/config.yml`; set it to `true` and queued tracks become ordinary discs
players can take out and keep. Removing a player's own disc through `remove` or `clear`
drops it beside the jukebox; nothing real is ever deleted.

### gui

Screens: `player`, `advanced`, `queue`, `speakers`, `broadcast` (these need a jukebox, `look`
by default), `lyrics` (the player's lyrics look), `settings` (CDisc's settings window, Paper
1.21.7+), `playlist <source>` (write a playlist onto blank discs), `close`.

The screen opens as if the player had opened it: CDisc's own permissions and WorldGuard
regions still apply to them.

```
/cdisc-api gui Steve queue 0,64,0,world
/cdisc-api gui @a close
```

### prefs

`lyrics <mode>` (`off`, `lyrics`, `track_lyrics`, `time_lyrics`, `track`, `time`),
`messages <on|off>` (now-playing chat lines), `scoreboard <on|off>` (lyrics in the sidebar).
Without a value it shows the current one.

## Permissions

All default to op. `cdisc.api.*` gives every one of them.

| Node | Covers |
|---|---|
| `cdisc.api.playsound` | `playsound` |
| `cdisc.api.control` | `stop`, `pause`, `resume`, `volume`, `distance`, `seek`, `loop`, `move` |
| `cdisc.api.list` | `list`, `info`, `jukeboxes`, `local` |
| `cdisc.api.jukebox` | `jukebox` |
| `cdisc.api.queue` | `queue` |
| `cdisc.api.gui` | `gui` |
| `cdisc.api.disc` | `disc` — it hands out real items |
| `cdisc.api.prefs` | `prefs` |
| `cdisc.api.reload` | `reload`, `reload cdisc` |

## Java API

Build the jar (below), `mvn install` it, and add it as a `provided` dependency:

```xml
<dependency>
    <groupId>dev.valkdz</groupId>
    <artifactId>cdisc-api</artifactId>
    <version>1.1.0</version>
    <scope>provided</scope>
</dependency>
```

`jitpack.yml` is in place, so once this repository is public, JitPack serves it as
`com.github.valkdzx:cdisc-api:<tag or commit>` from `https://jitpack.io`.

Declare it in your `plugin.yml`:

```yaml
depend: [CDisc-API]      # or softdepend
```

```java
CDiscAPI api = CDiscAPI.get();          // CDiscAPI.find() returns Optional instead of throwing

SoundHandle sound = api.play(SoundRequest.builder("local:victory.ogg")
        .following(player)               // or .at(location)
        .listener(player)                // only this player hears it; leave out for everyone nearby
        .volume(80)
        .distance(24)
        .loop(false)
        .id("victory-" + player.getName())
        .owner(this)                     // stopped automatically when your plugin disables
        .build());

sound.started().thenAccept(s -> getLogger().info("Now playing " + s.title()));
sound.ended().thenAccept(reason -> getLogger().info("Ended: " + reason));

sound.pause();
sound.seek(30_000);
sound.volume(40);
sound.moveTo(someLocation);
sound.stop();

api.sound("victory-Steve").ifPresent(SoundHandle::stop);

JukeboxControl jukebox = api.jukebox(block);
jukebox.play("https://youtu.be/dQw4w9WgXcQ");
jukebox.repeat(JukeboxControl.Repeat.TRACK);
jukebox.nowPlaying().ifPresent(np -> player.sendMessage(np.title()));

QueueControl queue = jukebox.queue();                 // slots 0-41 here
queue.add("yt:lofi hip hop").thenAccept(added -> getLogger().info("Queued " + added.get(0).title()));
queue.move(4, 0);
queue.play(0);

api.gui().open(player, GuiControl.Screen.QUEUE, block);
api.player(player).lyrics("track_lyrics");
api.createDisc("local:intro.ogg").thenAccept(disc -> player.getInventory().addItem(disc));
```

The queue, screens, discs and player settings are CDisc's own API, in
`dev.valkdz.cdisc.api` (`QueueControl`, `QueueEntry`, `GuiControl`, `PlayerSettings`,
`DiscInfo`). A plugin that needs only those can depend on CDisc alone, without this add-on;
see "Developer API" in CDisc's README for its Maven coordinates.

`play` returns at once; loading happens in the background. It throws `SoundRefusedException` when
CDisc is off, no voice chat plugin runs, the `max-sounds` limit is reached or a listener cancelled
it. A sound that fails later completes `started()` exceptionally with `SoundFailedException`, whose
`reason()` says why (`NO_MATCH`, `FAILED`, `TIMED_OUT`, ...).

Every method can be called from any thread.

### Events

| Event | When |
|---|---|
| `SoundPlayEvent` | Before a sound starts loading. Cancellable; `setRequest` can change it. Asynchronous when `play` was called off the main thread |
| `SoundStartEvent` | The track loaded and began playing |
| `SoundEndEvent` | The sound ended; `getReason()` says why |

CDisc's own events (`TrackStartEvent`, `PlaybackStopEvent`, `DiscCreateEvent` in
`dev.valkdz.cdisc.api.event`) keep working for jukeboxes.

## Building

```
mvn package
```

CDisc itself comes from JitPack, pinned by `cdisc.version` in `pom.xml` to a tag or commit of
[CDisc-Music](https://github.com/valkdzx/CDisc-Music). To build against a local CDisc checkout
instead, `mvn install` it and switch the dependency to `dev.valkdz:cdisc`.

The jar lands in `target/CDisc-API-<version>.jar`.
