package dev.toxi.aurionGo.feature.afk;

import dev.toxi.aurionGo.config.ConfigFile;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * Регистрирует плейсхолдер {@code %auriongo_afk%}: пустая строка, когда игрок
 * не в АФК, и настраиваемый индикатор (по умолчанию — золотые часы), когда в АФК.
 * Значение можно встроить в любой контекст PlaceholderAPI — чат, таб, скорборд.
 */
public final class AfkPlaceholderExpansion extends PlaceholderExpansion {

    private final AfkService service;
    private final ConfigFile config;
    private final String pluginVersion;

    public AfkPlaceholderExpansion(
        AfkService service,
        ConfigFile config,
        String pluginVersion
    ) {
        this.service = service;
        this.config = config;
        this.pluginVersion = pluginVersion;
    }

    @Override
    public String getIdentifier() {
        return "auriongo";
    }

    @Override
    public String getAuthor() {
        return "nora";
    }

    @Override
    public String getVersion() {
        return this.pluginVersion;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (!"afk".equalsIgnoreCase(params)) {
            return null;
        }

        boolean afk =
            player instanceof Player online && this.service.isAfk(online);
        String value = afk
            ? this.config.configuration().getString("indicator.afk", "&6⌚")
            : this.config.configuration().getString("indicator.online", "");

        return ChatColor.translateAlternateColorCodes(
            '&',
            value == null ? "" : value
        );
    }
}
