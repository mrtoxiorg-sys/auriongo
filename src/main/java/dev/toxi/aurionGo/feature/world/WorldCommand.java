package dev.toxi.aurionGo.feature.world;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class WorldCommand implements CommandExecutor {

    private final WorldService service;

    public WorldCommand(WorldService service) {
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

        if (!player.hasPermission("auriongo.command.misc.world")) {
            player.sendMessage(this.service.renderNoPermission());
            return true;
        }

        this.service.switchPlayer(player);
        return true;
    }
}
