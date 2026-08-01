package dev.toxi.aurionGo.feature.player.command;

import dev.toxi.aurionGo.feature.player.PlayerProfileService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ToggleNametagCommand implements CommandExecutor {

    private final PlayerProfileService profileService;

    public ToggleNametagCommand(PlayerProfileService profileService) {
        this.profileService = profileService;
    }

    @Override
    public boolean onCommand(
        CommandSender sender,
        Command command,
        String label,
        String[] args
    ) {
        if (!sender.hasPermission("auriongo.command.misc.togglenametag")) {
            this.profileService.sendNoPermission(sender);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(this.profileService.renderPlayerOnly());
            return true;
        }

        if (args.length != 0) {
            sender.sendMessage(this.profileService.renderToggleNametagUsage(label));
            return true;
        }

        sender.sendMessage(
            this.profileService.renderToggleNametagResult(
                this.profileService.toggleNametag(player)
            )
        );
        return true;
    }
}
