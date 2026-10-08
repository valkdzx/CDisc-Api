package dev.valkdz.cdiscapi.core;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.IllegalFormatException;

public final class Messages {

    private final FileConfiguration config;
    private final String prefix;

    public Messages(FileConfiguration config) {
        this.config = config;
        this.prefix = color(config.getString("messages.prefix", ""));
    }

    public String get(String key, Object... args) {
        String raw = config.getString("messages." + key);
        if (raw == null) raw = key;
        try {
            raw = args.length == 0 ? raw : String.format(raw, args);
        } catch (IllegalFormatException ignored) {
        }
        return prefix + color(raw);
    }

    public String plain(String text) {
        return prefix + color(text);
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
