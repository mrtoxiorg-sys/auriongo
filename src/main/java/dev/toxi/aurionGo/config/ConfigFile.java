package dev.toxi.aurionGo.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class ConfigFile {
    private final JavaPlugin plugin;
    private final ConfigDefinition definition;
    private final File file;
    private FileConfiguration configuration;

    public ConfigFile(JavaPlugin plugin, ConfigDefinition definition) {
        this.plugin = plugin;
        this.definition = definition;
        this.file = new File(plugin.getDataFolder(), definition.fileName());
    }

    public void load() {
        if (!this.plugin.getDataFolder().exists()) {
            this.plugin.getDataFolder().mkdirs();
        }

        if (!this.file.exists()) {
            this.plugin.saveResource(this.definition.fileName(), false);
        }

        this.configuration = YamlConfiguration.loadConfiguration(this.file);
    }

    public void reload() {
        this.configuration = YamlConfiguration.loadConfiguration(this.file);
    }

    public ConfigDefinition definition() {
        return this.definition;
    }

    public File file() {
        return this.file;
    }

    public FileConfiguration configuration() {
        return this.configuration;
    }
}
