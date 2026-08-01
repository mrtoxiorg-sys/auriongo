package dev.toxi.aurionGo.feature.integration;

import java.lang.reflect.Method;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class SuperVanishBridge {
    private static final String API_CLASS = "de.myzelyam.api.vanish.VanishAPI";

    private SuperVanishBridge() {}

    public static boolean isVanished(Plugin plugin, Player player) {
        if (player == null || plugin.getServer().getPluginManager().getPlugin("SuperVanish") == null) {
            return false;
        }

        try {
            Class<?> apiClass = Class.forName(API_CLASS);
            Method method = apiClass.getMethod("isInvisible", Player.class);
            Object result = method.invoke(null, player);
            return result instanceof Boolean vanished && vanished;
        } catch (Exception exception) {
            return false;
        }
    }

    public static boolean isHiddenFrom(Plugin plugin, Player viewer, Player target) {
        if (target == null) {
            return false;
        }

        return isVanished(plugin, target) && (viewer == null || !viewer.canSee(target));
    }
}
