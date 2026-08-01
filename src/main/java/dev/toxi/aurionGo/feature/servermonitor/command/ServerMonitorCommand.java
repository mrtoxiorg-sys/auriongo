package dev.toxi.aurionGo.feature.servermonitor.command;

import dev.toxi.aurionGo.command.CommandMessages;
import dev.toxi.aurionGo.feature.servermonitor.ServerMonitorService;
import dev.toxi.aurionGo.message.MessageFormatter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public final class ServerMonitorCommand implements CommandExecutor {
    private final ServerMonitorService service;
    private final MessageFormatter formatter;

    public ServerMonitorCommand(ServerMonitorService service) {
        this.service = service;
        this.formatter = service.formatter();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("auriongo.command.misc.servermonitor")) {
            CommandMessages.sendNotice(sender, this.formatter, "errors.no-permission", Map.of());
            return true;
        }

        if (!(sender instanceof Player player)) {
            CommandMessages.sendNotice(sender, this.formatter, "errors.player-only", Map.of());
            return true;
        }

        boolean enabled = this.service.toggle(player);
        CommandMessages.sendNotice(
                player,
                this.formatter,
                enabled ? "server-monitor.messages.enabled" : "server-monitor.messages.disabled",
                Map.of()
        );
        return true;
    }
}
