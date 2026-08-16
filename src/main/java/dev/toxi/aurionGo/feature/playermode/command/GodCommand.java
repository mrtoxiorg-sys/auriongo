package dev.toxi.aurionGo.feature.playermode.command;

import dev.toxi.aurionGo.feature.playermode.PlayerModeService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public final class GodCommand implements CommandExecutor, TabCompleter {

    private final PlayerModeService service;

    public GodCommand(PlayerModeService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("auriongo.command.misc.god")) {
            sender.sendMessage(this.service.renderNoPermission());
            return true;
        }

        if (args.length > 1) {
            sender.sendMessage(this.service.renderGodUsage());
            return true;
        }

        Player target = resolveTarget(sender, args.length == 1 ? args[0] : null);

        if (target == null) {
            return true;
        }

        this.service.applyGod(sender, target);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("auriongo.command.misc.god")) {
            return List.of();
        }

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }

        return List.of();
    }

    private Player resolveTarget(CommandSender sender, String input) {
        if (input == null) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(this.service.renderPlayerOnly());
                return null;
            }
            return player;
        }

        Player target = Bukkit.getPlayerExact(input);
        if (target == null) {
            sender.sendMessage(this.service.renderPlayerNotFound(input));
            return null;
        }
        return target;
    }
}