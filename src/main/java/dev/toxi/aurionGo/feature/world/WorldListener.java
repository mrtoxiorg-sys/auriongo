package dev.toxi.aurionGo.feature.world;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class WorldListener implements Listener {

    private final WorldService service;

    public WorldListener(WorldService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (
            !(event.getEntity() instanceof Player victim) ||
            !(event.getDamager() instanceof Player attacker)
        ) {
            return;
        }

        if (victim.getUniqueId().equals(attacker.getUniqueId())) {
            return;
        }

        this.service.markCombat(victim);
        this.service.markCombat(attacker);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        this.service.forget(event.getPlayer());
    }
}
