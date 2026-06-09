package dev.toxi.aurionGo.feature.player.command;

import dev.toxi.aurionGo.feature.player.PlayerProfileService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public final class CheckPlayerCommand implements CommandExecutor, TabCompleter {
    private final PlayerProfileService profileService;

    public CheckPlayerCommand(PlayerProfileService profileService) {
        this.profileService = profileService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("auriongo.player.check")) {
            this.profileService.sendNoPermission(sender);
            return true;
        }

        if (args.length != 1) {
            this.profileService.sendUsage(sender);
            return true;
        }

        this.profileService.sendPlayerInfo(sender, args[0]);
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
