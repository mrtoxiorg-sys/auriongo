package dev.toxi.aurionGo.feature.chat;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

public final class PlaceholderApiBridge {
    private final JavaPlugin plugin;

    public PlaceholderApiBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public String apply(Player player, String input) {
        Plugin placeholderApi = this.plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI");

        if (placeholderApi == null || !placeholderApi.isEnabled()) {
            return input;
        }

        try {
            Class<?> placeholderApiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method method = placeholderApiClass.getMethod("setPlaceholders", org.bukkit.OfflinePlayer.class, String.class);
            Object result = method.invoke(null, player, input);
            return result instanceof String string ? string : input;
        } catch (Exception exception) {
            return input;
        }
    }
}
