package dev.toxi.aurionGo.config;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ConfigManager {
    private final JavaPlugin plugin;
    private final Map<String, ConfigFile> configs = new LinkedHashMap<>();

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public ConfigFile register(ConfigDefinition definition) {
        ConfigFile configFile = new ConfigFile(this.plugin, definition);
        configFile.load();
        this.configs.put(definition.id(), configFile);
        return configFile;
    }

    public ConfigFile require(ConfigDefinition definition) {
        return require(definition.id());
    }

    public ConfigFile require(String id) {
        ConfigFile configFile = this.configs.get(id);

        if (configFile == null) {
            throw new IllegalStateException("Missing config registration: " + id);
        }

        return configFile;
    }

    public void reloadAll() {
        for (ConfigFile configFile : this.configs.values()) {
            configFile.reload();
        }
    }

    public Collection<ConfigFile> all() {
        return this.configs.values();
    }
}
