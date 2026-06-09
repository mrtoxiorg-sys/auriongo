package dev.toxi.aurionGo.feature.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ChatListener implements Listener {
    private final ChatService chatService;

    public ChatListener(ChatService chatService) {
        this.chatService = chatService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        this.chatService.configurePublicChat(event);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        event.joinMessage(this.chatService.createJoinMessage(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        this.chatService.clearReplyTarget(event.getPlayer());
        event.quitMessage(this.chatService.createQuitMessage(event.getPlayer()));
    }
}
