package dev.toxi.aurionGo.feature.player.command;

import dev.toxi.aurionGo.feature.player.PlayerProfileService;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class HideCommand implements CommandExecutor, TabCompleter {

    private static final List<String> OPTIONS = List.of(
        "joinleavemsg",
        "afkmsg"
    );

    private final PlayerProfileService profileService;

    public HideCommand(PlayerProfileService profileService) {
        this.profileService = profileService;
    }

    @Override
    public boolean onCommand(
        CommandSender sender,
        Command command,
        String label,
        String[] args
    ) {
        if (!sender.hasPermission("auriongo.command.misc.hide")) {
            this.profileService.sendNoPermission(sender);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(this.profileService.renderPlayerOnly());
            return true;
        }

        if (args.length != 1) {
            this.profileService.sendHideUsage(sender, label);
            return true;
        }

        if (args[0].equalsIgnoreCase("joinleavemsg")) {
            boolean enabled = this.profileService.toggleJoinLeaveMessages(
                player
            );
            this.profileService.sendHideToggleResult(
                sender,
                "joinleavemsg",
                enabled
            );
            return true;
        }

        if (args[0].equalsIgnoreCase("afkmsg")) {
            boolean enabled = this.profileService.toggleAfkMessages(player);
            this.profileService.sendHideToggleResult(sender, "afkmsg", enabled);
            return true;
        }

        this.profileService.sendHideUsage(sender, label);
        return true;
    }

    @Override
    public List<String> onTabComplete(
        CommandSender sender,
        Command command,
        String alias,
        String[] args
    ) {
        if (args.length != 1) {
            return List.of();
        }

        String partial = args[0].toLowerCase();
        return OPTIONS.stream()
            .filter(option -> option.startsWith(partial))
            .toList();
    }
}
