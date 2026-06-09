package dev.toxi.aurionGo.feature.chat.command;

import dev.toxi.aurionGo.feature.chat.ChatService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public final class ReplyCommand implements CommandExecutor {
    private final ChatService chatService;

    public ReplyCommand(ChatService chatService) {
        this.chatService = chatService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("auriongo.chat.reply")) {
            this.chatService.sendChatNotice(sender, "errors.no-permission", Map.of(), Map.of());
            return true;
        }

        if (!(sender instanceof Player player)) {
            this.chatService.sendChatNotice(sender, "errors.player-only", Map.of(), Map.of());
            return true;
        }

        if (args.length == 0) {
            this.chatService.sendChatNotice(player, "chat.commands.reply.usage", Map.of(), Map.of());
            return true;
        }

        this.chatService.sendReply(player, String.join(" ", args));
        return true;
    }
}
