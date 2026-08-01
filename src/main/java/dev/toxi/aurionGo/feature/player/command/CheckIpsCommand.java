package dev.toxi.aurionGo.feature.player.command;

import dev.toxi.aurionGo.feature.player.PlayerProfileService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class CheckIpsCommand implements CommandExecutor {
    private final PlayerProfileService profileService;

    public CheckIpsCommand(PlayerProfileService profileService) {
        this.profileService = profileService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("auriongo.command.misc.checkips")) {
            this.profileService.sendNoPermission(sender);
            return true;
        }

        if (args.length != 1) {
            this.profileService.sendIpUsage(sender);
            return true;
        }

        this.profileService.sendIpMatches(sender, args[0]);
        return true;
    }
}
