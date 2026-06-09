package dev.toxi.aurionGo.feature.punishment;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public final class PunishmentListener implements Listener {
    private final PunishmentService service;

    public PunishmentListener(PunishmentService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        Component kickMessage = this.service.createBanScreen(event.getUniqueId());

        if (kickMessage != null) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, kickMessage);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Component blocked = this.service.createMuteBlockMessage(event.getPlayer().getUniqueId());

        if (blocked == null) {
            return;
        }

        event.setCancelled(true);
        this.service.sendMuteBlockMessage(event.getPlayer(), blocked);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!this.service.shouldBlockMutedCommand(event.getPlayer().getUniqueId(), event.getMessage())) {
            return;
        }

        Component blocked = this.service.createMuteBlockMessage(event.getPlayer().getUniqueId());

        if (blocked == null) {
            return;
        }

        event.setCancelled(true);
        event.getPlayer().sendActionBar(blocked);
    }
}
