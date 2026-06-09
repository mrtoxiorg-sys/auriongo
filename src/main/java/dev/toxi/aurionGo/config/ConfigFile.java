package dev.toxi.aurionGo.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

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

        YamlConfiguration bundledConfiguration = loadBundledConfiguration();

        if (bundledConfiguration != null && shouldReplaceWithBundled(bundledConfiguration)) {
            backupCurrentFile();
            this.plugin.saveResource(this.definition.fileName(), true);
        }

        this.configuration = YamlConfiguration.loadConfiguration(this.file);

        if (bundledConfiguration != null) {
            this.configuration.setDefaults(bundledConfiguration);
            this.configuration.options().copyDefaults(true);
            save();
        }
    }

    public void reload() {
        load();
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

    private YamlConfiguration loadBundledConfiguration() {
        InputStream resourceStream = this.plugin.getResource(this.definition.fileName());

        if (resourceStream == null) {
            return null;
        }

        try (InputStream inputStream = resourceStream;
             InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось загрузить встроенный конфиг: " + this.definition.fileName(), exception);
        }
    }

    private boolean shouldReplaceWithBundled(YamlConfiguration bundledConfiguration) {
        YamlConfiguration currentConfiguration = YamlConfiguration.loadConfiguration(this.file);
        int currentVersion = currentConfiguration.getInt("config-version", 0);
        int bundledVersion = bundledConfiguration.getInt("config-version", 0);
        return bundledVersion > currentVersion;
    }

    private void backupCurrentFile() {
        if (!this.file.exists()) {
            return;
        }

        File backupFile = new File(this.file.getParentFile(), this.file.getName() + ".bak");

        try {
            Files.copy(this.file.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось создать резервную копию конфига: " + this.file.getName(), exception);
        }
    }

    private void save() {
        try {
            this.configuration.save(this.file);
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось сохранить конфиг: " + this.file.getName(), exception);
        }
    }
}
