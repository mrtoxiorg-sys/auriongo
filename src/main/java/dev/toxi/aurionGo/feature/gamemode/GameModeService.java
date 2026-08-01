package dev.toxi.aurionGo.feature.gamemode;

import dev.toxi.aurionGo.message.MessageFormatter;
import dev.toxi.aurionGo.shared.AurionContext;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class GameModeService {
    private final MessageFormatter messageFormatter;

    public GameModeService(AurionContext context) {
        this.messageFormatter = context.serviceRegistry().require(MessageFormatter.class);
    }

    public void apply(CommandSender sender, GameMode mode, Player target) {
        target.setGameMode(mode);

        boolean self = sender instanceof Player player && player.getUniqueId().equals(target.getUniqueId());
        String modeName = modeName(mode);
        target.sendMessage(render(
                self ? "gamemode.messages.self-change" : "gamemode.messages.target-change",
                Map.of(
                        "mode", modeName,
                        "target", target.getName(),
                        "actor", sender.getName()
                )
        ));

        if (!self) {
            sender.sendMessage(render(
                    "gamemode.messages.other-change",
                    Map.of(
                            "mode", modeName,
                            "target", target.getName(),
                            "actor", sender.getName()
                    )
            ));
        }

        notifyStaff(sender, target, modeName, self);
    }

    public Component renderGenericUsage() {
        return render("gamemode.usage.gm", Map.of());
    }

    public Component renderShortcutUsage(String label) {
        return render("gamemode.usage.shortcut", Map.of("command", label.toLowerCase()));
    }

    public Component renderNoPermission() {
        return render("errors.no-permission", Map.of());
    }

    public Component renderPlayerOnly() {
        return render("errors.player-only", Map.of());
    }

    public Component renderPlayerNotFound(String input) {
        return render("errors.invalid-player", Map.of("player", input));
    }

    public Collection<String> suggestModeNames(String input) {
        List<String> values = List.of("creative", "survival", "spectator", "adventure", "c", "s", "sp", "a");
        String partial = input.toLowerCase();
        return values.stream().filter(value -> value.startsWith(partial)).toList();
    }

    public String modeName(GameMode mode) {
        return this.messageFormatter.getOrDefault("gamemode.modes." + mode.name().toLowerCase(), mode.name());
    }

    private void notifyStaff(CommandSender actor, Player target, String modeName, boolean self) {
        Component notification = render(
                self ? "gamemode.messages.staff-notify-self" : "gamemode.messages.staff-notify-other",
                Map.of(
                        "actor", actor.getName(),
                        "target", target.getName(),
                        "mode", modeName
                )
        );

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission("auriongo.command.misc.gamemode.notify") && !player.hasPermission("auriongo.command.misc.gamemode")) {
                continue;
            }

            if (actor instanceof Player actorPlayer && actorPlayer.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }

            if (target.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }

            player.sendMessage(notification);
        }
    }

    private Component render(String path, Map<String, String> placeholders) {
        return this.messageFormatter.renderRaw(
                this.messageFormatter.getOrDefault(path, "<color:#FF4F4F>Отсутствует шаблон: " + path),
                placeholders
        );
    }
}
