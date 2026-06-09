package dev.toxi.aurionGo.feature.punishment.command;

import dev.toxi.aurionGo.feature.punishment.PunishmentService;
import dev.toxi.aurionGo.feature.punishment.PunishmentType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

public final class PunishmentListCommand implements CommandExecutor, TabCompleter {
    private final PunishmentService service;
    private final PunishmentType type;

    public PunishmentListCommand(PunishmentService service, PunishmentType type) {
        this.service = service;
        this.type = type;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("auriongo.punish." + this.type.key() + ".list")) {
            sender.sendMessage(this.service.renderNoPermission());
            return true;
        }

        int page = 1;

        if (args.length >= 1) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
                page = 1;
            }
        }

        this.service.listPunishments(sender, this.type, page);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }

        return List.of("1", "2", "3", "4", "5").stream()
                .filter(value -> value.startsWith(args[0]))
                .toList();
    }
}
