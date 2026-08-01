package dev.toxi.aurionGo.feature.chat.command;

import dev.toxi.aurionGo.feature.chat.ChatService;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class BroadcastCommand implements CommandExecutor {

    private final ChatService chatService;

    public BroadcastCommand(ChatService chatService) {
        this.chatService = chatService;
    }

    @Override
    public boolean onCommand(
        CommandSender sender,
        Command command,
        String label,
        String[] args
    ) {
        if (!sender.hasPermission("auriongo.command.chat.broadcast")) {
            this.chatService.sendChatNotice(sender, "errors.no-permission", Map.of(), Map.of());
            return true;
        }

        if (args.length == 0) {
            this.chatService.sendChatNotice(sender, "chat.commands.broadcast.usage", Map.of(), Map.of());
            return true;
        }

        this.chatService.broadcastAnnouncement(sender, String.join(" ", args));
        return true;
    }
}
