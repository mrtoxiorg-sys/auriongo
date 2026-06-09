package dev.toxi.aurionGo.feature.player;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class PlayerDataListener implements Listener {
    private final PlayerProfileService profileService;

    public PlayerDataListener(PlayerProfileService profileService) {
        this.profileService = profileService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        this.profileService.trackJoin(event.getPlayer());
    }
}
