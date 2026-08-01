package dev.toxi.aurionGo.feature.punishment.command;

import dev.toxi.aurionGo.feature.punishment.PunishmentService;
import dev.toxi.aurionGo.feature.punishment.PunishmentType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

public final class PunishmentRemoveCommand implements CommandExecutor, TabCompleter {
    private final PunishmentService service;
    private final PunishmentType type;

    public PunishmentRemoveCommand(PunishmentService service, PunishmentType type) {
        this.service = service;
        this.type = type;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("auriongo.command.punishment.un" + this.type.key())) {
            sender.sendMessage(this.service.renderNoPermission());
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(this.service.renderRemoveUsage(this.type));
            return true;
        }

        this.service.removePunishment(sender, this.type, args[0]);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }

        return this.service.suggestActiveTargets(this.type, args[0]);
    }
}
