package dev.toxi.aurionGo.feature.gamemode.command;

import dev.toxi.aurionGo.feature.gamemode.GameModeService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public final class GameModeCommand implements CommandExecutor, TabCompleter {
    private final GameModeService service;
    private final GameMode fixedMode;

    public GameModeCommand(GameModeService service, GameMode fixedMode) {
        this.service = service;
        this.fixedMode = fixedMode;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("auriongo.gamemode")) {
            sender.sendMessage(this.service.renderNoPermission());
            return true;
        }

        if (this.fixedMode == null) {
            return handleGeneric(sender, args);
        }

        return handleShortcut(sender, args, this.fixedMode, label);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (this.fixedMode == null) {
            if (args.length == 1) {
                return List.copyOf(this.service.suggestModeNames(args[0]));
            }

            if (args.length == 2) {
                return suggestPlayers(args[1]);
            }

            return List.of();
        }

        if (args.length == 1) {
            return suggestPlayers(args[0]);
        }

        return List.of();
    }

    private boolean handleGeneric(CommandSender sender, String[] args) {
        if (args.length < 1 || args.length > 2) {
            sender.sendMessage(this.service.renderGenericUsage());
            return true;
        }

        GameMode mode = parseMode(args[0]);

        if (mode == null) {
            sender.sendMessage(this.service.renderGenericUsage());
            return true;
        }

        Player target = resolveTarget(sender, args.length == 2 ? args[1] : null);

        if (target == null) {
            return true;
        }

        this.service.apply(sender, mode, target);
        return true;
    }

    private boolean handleShortcut(CommandSender sender, String[] args, GameMode mode, String commandLabel) {
        if (args.length > 1) {
            sender.sendMessage(this.service.renderShortcutUsage(commandLabel));
            return true;
        }

        Player target = resolveTarget(sender, args.length == 1 ? args[0] : null);

        if (target == null) {
            return true;
        }

        this.service.apply(sender, mode, target);
        return true;
    }

    private Player resolveTarget(CommandSender sender, String input) {
        if (input == null) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(this.service.renderPlayerOnly());
                return null;
            }

            return player;
        }

        if (!sender.hasPermission("auriongo.gamemode.others")) {
            sender.sendMessage(this.service.renderNoPermission());
            return null;
        }

        Player target = Bukkit.getPlayerExact(input);

        if (target == null) {
            sender.sendMessage(this.service.renderPlayerNotFound(input));
            return null;
        }

        return target;
    }

    private GameMode parseMode(String input) {
        return switch (input.toLowerCase()) {
            case "creative", "c", "1" -> GameMode.CREATIVE;
            case "survival", "s", "0" -> GameMode.SURVIVAL;
            case "spectator", "sp", "3" -> GameMode.SPECTATOR;
            case "adventure", "a", "2" -> GameMode.ADVENTURE;
            default -> null;
        };
    }

    private List<String> suggestPlayers(String input) {
        String partial = input.toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(partial))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}
