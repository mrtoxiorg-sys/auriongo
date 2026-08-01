package dev.toxi.aurionGo.feature.afk.command;

import dev.toxi.aurionGo.feature.afk.AfkService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class AfkCommand implements CommandExecutor {

    private static final String PERMISSION = "auriongo.command.misc.afk";

    private final AfkService service;

    public AfkCommand(AfkService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(
        CommandSender sender,
        Command command,
        String label,
        String[] args
    ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(this.service.renderPlayerOnly());
            return true;
        }

        if (!player.hasPermission(PERMISSION)) {
            player.sendMessage(this.service.renderNoPermission());
            return true;
        }

        this.service.toggle(player);
        return true;
    }
}
