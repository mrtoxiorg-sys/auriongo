package dev.toxi.aurionGo.feature.player;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class PlayerDataListener implements Listener {

    private final PlayerProfileService profileService;

    public PlayerDataListener(PlayerProfileService profileService) {
        this.profileService = profileService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        this.profileService.trackJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        this.profileService.trackQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (!event.getPlayer().isSneaking()) {
            return;
        }

        if (!(event.getRightClicked() instanceof Player target)) {
            return;
        }

        this.profileService.sendNicknameActionBar(event.getPlayer(), target);
    }
}
