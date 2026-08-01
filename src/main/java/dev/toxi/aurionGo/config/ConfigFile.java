package dev.toxi.aurionGo.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.ConfigurationSection;
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

        // Грузим файл вручную, чтобы отличить синтаксическую ошибку от пустого конфига.
        this.configuration = new YamlConfiguration();
        boolean parseFailed = false;
        try {
            this.configuration.load(this.file);
        } catch (InvalidConfigurationException exception) {
            parseFailed = true;
            this.plugin.getLogger().severe(
                "Конфиг " + this.definition.fileName() + " содержит ошибку YAML и НЕ был перезагружен. "
                + "Файл оставлен без изменений — исправьте синтаксис и повторите reload. Причина: "
                + exception.getMessage()
            );
        } catch (IOException exception) {
            parseFailed = true;
            this.plugin.getLogger().severe(
                "Не удалось прочитать конфиг " + this.definition.fileName() + ": " + exception.getMessage()
            );
        }

        if (bundledConfiguration == null) {
            return;
        }

        // При ошибке парсинга файл НЕ трогаем (чтобы не затереть правки дефолтом из-за опечатки),
        // а встроенные дефолты используем только в памяти на эту сессию.
        if (parseFailed) {
            this.configuration.setDefaults(bundledConfiguration);
            this.configuration.options().copyDefaults(true);
            return;
        }

        int currentVersion = this.configuration.getInt("config-version", 0);
        int bundledVersion = bundledConfiguration.getInt("config-version", 0);

        // Проверяем ДО подмешивания дефолтов, каких ключей реально нет в пользовательском файле.
        // isSet() игнорирует defaults, поэтому видит только то, что физически записано на диске.
        boolean missingKeys = copyMissingKeys(bundledConfiguration);

        // Домешиваем дефолты как fallback: новые ключи становятся доступны через getter'ы,
        // но значения, заданные пользователем, НЕ перезаписываются.
        this.configuration.setDefaults(bundledConfiguration);
        this.configuration.options().copyDefaults(true);

        boolean needsSave = missingKeys;

        // Миграция версии = только обновление номера и добавление новых ключей.
        // Файл целиком дефолтом НЕ перезаписывается, чтобы не терять ручные правки.
        if (bundledVersion > currentVersion) {
            backupCurrentFile();
            this.configuration.set("config-version", bundledVersion);
            needsSave = true;
        }

        // Пишем на диск только если что-то реально изменилось (новые ключи или версия).
        // Иначе файл не трогаем — сохраняются комментарии, форматирование и правки пользователя.
        if (needsSave) {
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

    private boolean copyMissingKeys(YamlConfiguration bundledConfiguration) {
        boolean missingKeys = false;

        for (String key : bundledConfiguration.getKeys(true)) {
            if (this.configuration.isSet(key)) {
                continue;
            }

            Object value = bundledConfiguration.get(key);

            if (value instanceof ConfigurationSection || value == null) {
                continue;
            }

            this.configuration.set(key, value);
            missingKeys = true;
        }

        return missingKeys;
    }
}
