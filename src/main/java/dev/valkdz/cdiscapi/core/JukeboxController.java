package dev.valkdz.cdiscapi.core;

import dev.valkdz.cdisc.api.CDiscApi;
import dev.valkdz.cdisc.api.NowPlaying;
import dev.valkdz.cdisc.api.QueueControl;
import dev.valkdz.cdiscapi.api.JukeboxControl;
import org.bukkit.block.Block;

import java.util.Optional;

final class JukeboxController implements JukeboxControl {

    private final Block block;

    JukeboxController(Block block) {
        this.block = block;
    }

    private dev.valkdz.cdisc.api.JukeboxControl cdisc() {
        return CDiscApi.jukebox(block);
    }

    @Override
    public Block block() {
        return block;
    }

    @Override
    public boolean isActive() {
        return cdisc().isActive();
    }

    @Override
    public boolean isPaused() {
        return cdisc().isPaused();
    }

    @Override
    public Optional<NowPlaying> nowPlaying() {
        return cdisc().nowPlaying();
    }

    @Override
    public int queueSize() {
        return cdisc().queueSize();
    }

    @Override
    public QueueControl queue() {
        return cdisc().queue();
    }

    @Override
    public void play(String source) {
        cdisc().play(source);
    }

    @Override
    public void resumeQueue() {
        cdisc().resumeQueue();
    }

    @Override
    public void stop() {
        cdisc().stop();
    }

    @Override
    public void pause() {
        cdisc().pause();
    }

    @Override
    public void resume() {
        cdisc().resume();
    }

    @Override
    public void skip() {
        cdisc().skip();
    }

    @Override
    public void previous() {
        cdisc().previous();
    }

    @Override
    public void seek(long positionMs) {
        cdisc().seek(positionMs);
    }

    @Override
    public Repeat repeat() {
        return Repeat.valueOf(cdisc().repeat().name());
    }

    @Override
    public void repeat(Repeat mode) {
        cdisc().repeat(dev.valkdz.cdisc.api.JukeboxControl.Repeat.valueOf(mode.name()));
    }

    @Override
    public boolean shuffle() {
        return cdisc().shuffle();
    }

    @Override
    public void shuffle(boolean on) {
        cdisc().shuffle(on);
    }

    @Override
    public int volume() {
        return cdisc().volume();
    }

    @Override
    public void volume(int volume) {
        cdisc().volume(volume);
    }
}
