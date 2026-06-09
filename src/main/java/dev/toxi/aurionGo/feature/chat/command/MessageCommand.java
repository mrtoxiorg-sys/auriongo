package dev.toxi.aurionGo.feature.chat.command;

import dev.toxi.aurionGo.feature.chat.ChatService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public final class MessageCommand implements CommandExecutor, TabCompleter {
    private final ChatService chatService;

    public MessageCommand(ChatService chatService) {
        this.chatService = chatService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("auriongo.chat.msg")) {
            this.chatService.sendChatNotice(sender, "errors.no-permission", Map.of(), Map.of());
            return true;
        }

        if (!(sender instanceof Player player)) {
            this.chatService.sendChatNotice(sender, "messages.player-only", Map.of(), Map.of());
            return true;
        }

        if (args.length < 2) {
            this.chatService.sendChatNotice(player, "commands.msg.usage", Map.of(), Map.of());
            return true;
        }

        Player target = this.chatService.findOnlinePlayer(args[0]);

        if (target == null) {
            this.chatService.sendChatNotice(player, "messages.player-not-found", Map.of("target", args[0]), Map.of());
            return true;
        }

        this.chatService.sendPrivateMessage(player, target, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }

        String partial = args[0].toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(partial))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}
