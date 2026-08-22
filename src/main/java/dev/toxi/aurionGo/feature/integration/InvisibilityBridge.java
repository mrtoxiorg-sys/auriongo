package dev.toxi.aurionGo.feature.integration;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;

public final class InvisibilityBridge {

    private InvisibilityBridge() {}

    public static boolean isInvisible(Entity entity) {
        if (entity == null) {
            return false;
        }

        if (entity.isInvisible()) {
            return true;
        }

        return entity instanceof LivingEntity living &&
            living.hasPotionEffect(PotionEffectType.INVISIBILITY);
    }

    public static boolean isConcealed(Plugin plugin, Entity entity) {
        if (isInvisible(entity)) {
            return true;
        }

        return entity instanceof Player player &&
            SuperVanishBridge.isVanished(plugin, player);
    }

    public static boolean isConcealedFrom(
        Plugin plugin,
        Player viewer,
        Entity target
    ) {
        if (target == null) {
            return false;
        }

        if (
            viewer != null &&
            target instanceof Player targetPlayer &&
            !viewer.canSee(targetPlayer)
        ) {
            return true;
        }

        return isConcealed(plugin, target);
    }
}
