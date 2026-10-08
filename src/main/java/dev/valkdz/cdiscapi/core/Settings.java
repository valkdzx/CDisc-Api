package dev.valkdz.cdiscapi.core;

import org.bukkit.configuration.file.FileConfiguration;

public record Settings(int defaultVolume, float defaultDistance, int maxSounds, int maxVolume,
                       int maxDistance, long loadTimeoutMs) {

    public static Settings read(FileConfiguration config) {
        return new Settings(
                config.getInt("defaults.volume", -1),
                (float) Math.max(0.0, config.getDouble("defaults.distance", 0.0)),
                Math.max(0, config.getInt("limits.max-sounds", 64)),
                Math.max(0, config.getInt("limits.max-volume", 200)),
                Math.max(1, config.getInt("limits.max-distance", 256)),
                Math.max(5, config.getInt("limits.load-timeout", 30)) * 1000L);
    }
}
