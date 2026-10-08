package dev.valkdz.cdiscapi.api;

import dev.valkdz.cdisc.api.DiscInfo;
import dev.valkdz.cdisc.api.GuiControl;
import dev.valkdz.cdisc.api.PlayerSettings;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface CDiscAPI {

    static CDiscAPI get() {
        RegisteredServiceProvider<CDiscAPI> provider =
                Bukkit.getServicesManager().getRegistration(CDiscAPI.class);
        if (provider == null) throw new IllegalStateException("CDisc-API is not enabled");
        return provider.getProvider();
    }

    static Optional<CDiscAPI> find() {
        RegisteredServiceProvider<CDiscAPI> provider =
                Bukkit.getServicesManager().getRegistration(CDiscAPI.class);
        return provider == null ? Optional.empty() : Optional.of(provider.getProvider());
    }

    boolean isVoiceReady();

    SoundHandle play(SoundRequest request);

    Optional<SoundHandle> sound(String id);

    Collection<SoundHandle> sounds();

    int stopAll();

    JukeboxControl jukebox(Block block);

    Collection<Block> playingJukeboxes();

    List<String> localTracks();

    GuiControl gui();

    PlayerSettings player(Player player);

    CompletableFuture<ItemStack> createDisc(String source);

    CompletableFuture<ItemStack> createDisc(String source, String title, String author);

    Optional<DiscInfo> readDisc(ItemStack item);

    void reloadCDisc();
}
