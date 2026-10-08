package dev.valkdz.cdiscapi;

import dev.valkdz.cdiscapi.api.CDiscAPI;
import dev.valkdz.cdiscapi.command.ApiCommand;
import dev.valkdz.cdiscapi.core.Compat;
import dev.valkdz.cdiscapi.core.Messages;
import dev.valkdz.cdiscapi.core.Settings;
import dev.valkdz.cdiscapi.core.SoundService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class CDiscApiPlugin extends JavaPlugin {

    private volatile Settings settings;
    private volatile Messages messages;
    private SoundService service;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSettings();

        Plugin cdisc = getServer().getPluginManager().getPlugin("CDisc");
        if (cdisc == null || !cdisc.isEnabled()) {
            getLogger().severe("CDisc is not enabled, and CDisc-API cannot work without it.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        List<String> missing = Compat.missing();
        if (!missing.isEmpty()) {
            getLogger().severe("CDisc " + cdisc.getDescription().getVersion()
                    + " is not compatible with this CDisc-API build. Missing: " + String.join(", ", missing));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        service = new SoundService(this);
        service.start();
        getServer().getServicesManager().register(CDiscAPI.class, service, this, ServicePriority.Normal);
        getServer().getPluginManager().registerEvents(service, this);

        ApiCommand command = new ApiCommand(this, service);
        PluginCommand registered = getCommand("cdisc-api");
        if (registered != null) {
            registered.setExecutor(command);
            registered.setTabCompleter(command);
        }

        getLogger().info("Hooked into CDisc " + cdisc.getDescription().getVersion() + ".");
    }

    @Override
    public void onDisable() {
        if (service != null) {
            service.shutdown();
            service = null;
        }
        getServer().getServicesManager().unregisterAll(this);
    }

    public void reloadSettings() {
        reloadConfig();
        settings = Settings.read(getConfig());
        messages = new Messages(getConfig());
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }
}
