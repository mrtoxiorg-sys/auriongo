package dev.toxi.aurionGo.feature.punishment.command;

import dev.toxi.aurionGo.feature.punishment.PunishmentService;
import dev.toxi.aurionGo.feature.punishment.PunishmentType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

public final class PunishmentListSearchCommand implements CommandExecutor, TabCompleter {
    private final PunishmentService service;
    private final PunishmentType type;

    public PunishmentListSearchCommand(PunishmentService service, PunishmentType type) {
        this.service = service;
        this.type = type;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("auriongo.command.punishment." + this.type.key() + "list")) {
            sender.sendMessage(this.service.renderNoPermission());
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(this.service.renderListSearchUsage(this.type));
            return true;
        }

        String query = args[0];
        int page = 1;

        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
                page = 1;
            }
        }

        this.service.searchPunishments(sender, this.type, query, page);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return this.service.suggestActiveTargets(this.type, args[0]);
        }

        if (args.length == 2) {
            return List.of("1", "2", "3", "4", "5").stream()
                    .filter(value -> value.startsWith(args[1]))
                    .toList();
        }

        return List.of();
    }
}