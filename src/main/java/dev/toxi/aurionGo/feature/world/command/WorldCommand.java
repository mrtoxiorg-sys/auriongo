package dev.toxi.aurionGo.feature.world.command;

import dev.toxi.aurionGo.feature.world.WorldService;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class WorldCommand implements CommandExecutor {

    private static final String PERMISSION = "auriongo.command.misc.world";

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
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(
                this.service.formatter().render("errors.no-permission", Map.of())
            );
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(
                this.service.formatter().render("errors.player-only", Map.of())
            );
            return true;
        }

        this.service.requestSwitch(player);
        return true;
    }
}
