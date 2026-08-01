package dev.toxi.aurionGo.feature.chat.command;

import dev.toxi.aurionGo.feature.chat.ChatService;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class SpyCommand implements CommandExecutor {

    private final ChatService chatService;

    public SpyCommand(ChatService chatService) {
        this.chatService = chatService;
    }

    @Override
    public boolean onCommand(
        CommandSender sender,
        Command command,
        String label,
        String[] args
    ) {
        if (!sender.hasPermission("auriongo.command.chat.spy")) {
            this.chatService.sendChatNotice(sender, "errors.no-permission", Map.of(), Map.of());
            return true;
        }

        if (!(sender instanceof Player player)) {
            this.chatService.sendChatNotice(sender, "errors.player-only", Map.of(), Map.of());
            return true;
        }

        boolean enabled = this.chatService.toggleSpy(player);
        this.chatService.sendChatNotice(
            player,
            enabled ? "chat.messages.spy-enabled" : "chat.messages.spy-disabled",
            Map.of(),
            Map.of()
        );
        return true;
    }
}
