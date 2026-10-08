package dev.valkdz.cdiscapi.core;

import java.util.ArrayList;
import java.util.List;

// CDisc's internals carry no compatibility promise, so every member this addon touches is
// checked up front; a rename would otherwise surface as a NoSuchMethodError mid-playback.
public final class Compat {

    private static final String[][] REQUIRED = {
            {"dev.valkdz.cdisc.Main", "getInstance"},
            {"dev.valkdz.cdisc.Main", "getAudioPlayerManager"},
            {"dev.valkdz.cdisc.Main", "getLocalMusic"},
            {"dev.valkdz.cdisc.Main", "cdiscConfig"},
            {"dev.valkdz.cdisc.jukebox.PlaybackManager", "createFollowingSession"},
            {"dev.valkdz.cdisc.jukebox.PlaybackManager", "hasVoiceBackend"},
            {"dev.valkdz.cdisc.jukebox.PlaybackManager", "getTrackLoader"},
            {"dev.valkdz.cdisc.jukebox.PlaybackManager", "getAnchorManager"},
            {"dev.valkdz.cdisc.jukebox.PlaybackManager", "getBaseDistance"},
            {"dev.valkdz.cdisc.audio.source.TrackLoader", "resolveQuery"},
            {"dev.valkdz.cdisc.audio.source.TrackLoader", "load"},
            {"dev.valkdz.cdisc.audio.source.TrackLoader", "createPlayer"},
            {"dev.valkdz.cdisc.audio.source.TrackLoader", "hasNextSource"},
            {"dev.valkdz.cdisc.audio.source.TrackLoader", "nextSourceAsync"},
            {"dev.valkdz.cdisc.jukebox.AudioSession", "replaceVoiceSession"},
            {"dev.valkdz.cdisc.jukebox.AudioSession", "positionOf"},
            {"dev.valkdz.cdisc.jukebox.AudioSession", "seeking"},
            {"dev.valkdz.cdisc.jukebox.AudioSession", "allOutputs"},
            {"dev.valkdz.cdisc.voice.anchor.AnchorManager", "createAt"},
            {"dev.valkdz.cdisc.voice.anchor.AnchorManager", "moveTo"},
            {"dev.valkdz.cdisc.voice.anchor.SoundAnchor", "followAt"},
            {"dev.valkdz.cdisc.voice.anchor.SoundAnchor", "parkAt"},
            {"dev.valkdz.cdisc.feature.local.LocalMusicLibrary", "shown"},
            {"dev.valkdz.cdisc.feature.local.LocalMusicLibrary", "complete"},
            {"dev.valkdz.cdisc.util.Tasks", "onEntity"},
            {"dev.valkdz.cdisc.api.CDiscApi", "jukebox"},
            {"dev.valkdz.cdisc.api.CDiscApi", "queue"},
            {"dev.valkdz.cdisc.api.CDiscApi", "gui"},
            {"dev.valkdz.cdisc.api.CDiscApi", "player"},
            {"dev.valkdz.cdisc.api.CDiscApi", "createDisc"},
            {"dev.valkdz.cdisc.api.CDiscApi", "readDisc"},
            {"dev.valkdz.cdisc.api.CDiscApi", "reload"},
    };

    private Compat() {
    }

    public static List<String> missing() {
        List<String> missing = new ArrayList<>();
        for (String[] member : REQUIRED) {
            try {
                Class<?> type = Class.forName(member[0], false, Compat.class.getClassLoader());
                boolean found = false;
                for (java.lang.reflect.Method method : type.getMethods()) {
                    if (method.getName().equals(member[1])) {
                        found = true;
                        break;
                    }
                }
                if (!found) missing.add(type.getSimpleName() + "." + member[1]);
            } catch (ClassNotFoundException | LinkageError e) {
                missing.add(member[0]);
            }
        }
        return missing;
    }
}
