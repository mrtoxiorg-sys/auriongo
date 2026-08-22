package dev.toxi.aurionGo.feature.spectral;

import dev.toxi.aurionGo.feature.integration.InvisibilityBridge;
import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.LingeringPotionSplashEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class SpectralPotionListener implements Listener {

    private static final byte CLOUD_MARKER = 1;

    private final Plugin plugin;
    private final SpectralItems items;
    private final SpectralSettings settings;
    private final NamespacedKey cloudKey;

    public SpectralPotionListener(
        Plugin plugin,
        SpectralItems items,
        SpectralSettings settings
    ) {
        this.plugin = plugin;
        this.items = items;
        this.settings = settings;
        this.cloudKey = new NamespacedKey(plugin, "spectral_cloud");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        if (!this.items.isSpectral(event.getPotion())) {
            return;
        }

        Collection<LivingEntity> affected = event.getAffectedEntities();

        for (LivingEntity entity : affected) {
            event.setIntensity(entity, 0.0D);

            if (isEligible(entity)) {
                entity.addPotionEffect(glowEffect(randomGlowTicks()));
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onLingeringSplash(LingeringPotionSplashEvent event) {
        if (!this.items.isSpectral(event.getEntity().getItem())) {
            return;
        }

        AreaEffectCloud cloud = event.getAreaEffectCloud();
        cloud.getPersistentDataContainer().set(
            this.cloudKey,
            PersistentDataType.BYTE,
            CLOUD_MARKER
        );
        cloud.clearCustomEffects();
        cloud.addCustomEffect(glowEffect(randomGlowTicks()), true);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCloudApply(AreaEffectCloudApplyEvent event) {
        if (!isSpectralCloud(event.getEntity())) {
            return;
        }

        event.getAffectedEntities().removeIf(entity -> !isEligible(entity));
    }

    private boolean isSpectralCloud(AreaEffectCloud cloud) {
        return cloud
            .getPersistentDataContainer()
            .has(this.cloudKey, PersistentDataType.BYTE);
    }

    private PotionEffect glowEffect(int durationTicks) {
        return new PotionEffect(
            PotionEffectType.GLOWING,
            durationTicks,
            0,
            false,
            true,
            true
        );
    }

    private boolean isEligible(LivingEntity entity) {
        if (!entity.isValid()) {
            return false;
        }

        if (!(entity instanceof Player) && !this.settings.affectMobs()) {
            return false;
        }

        if (!this.settings.onlyInvisible()) {
            return true;
        }

        return InvisibilityBridge.isConcealed(this.plugin, entity);
    }

    private int randomGlowTicks() {
        int min = this.settings.minGlowTicks();
        int max = this.settings.maxGlowTicks();

        if (max <= min) {
            return min;
        }

        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }
}
