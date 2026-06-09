package dev.toxi.aurionGo.feature.gamemode;

import dev.toxi.aurionGo.config.StandardConfigs;
import dev.toxi.aurionGo.shared.AurionContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class GameModeService {
    private final AurionContext context;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public GameModeService(AurionContext context) {
        this.context = context;
    }

    public void apply(CommandSender sender, GameMode mode, Player target) {
        target.setGameMode(mode);

        boolean self = sender instanceof Player player && player.getUniqueId().equals(target.getUniqueId());
        String modeName = modeName(mode);
        target.sendMessage(render(
                self ? "messages.self-change" : "messages.target-change",
                Map.of(
                        "mode", modeName,
                        "target", target.getName(),
                        "actor", sender.getName()
                )
        ));

        if (!self) {
            sender.sendMessage(render(
                    "messages.other-change",
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
        return render("usage.gm", Map.of());
    }

    public Component renderShortcutUsage(String label) {
        return render("usage.shortcut", Map.of("command", label.toLowerCase()));
    }

    public Component renderNoPermission() {
        return renderMessages("errors.no-permission", Map.of());
    }

    public Component renderPlayerOnly() {
        return renderMessages("errors.player-only", Map.of());
    }

    public Component renderPlayerNotFound(String input) {
        return renderMessages("errors.invalid-player", Map.of("player", input));
    }

    public Collection<String> suggestModeNames(String input) {
        List<String> values = List.of("creative", "survival", "spectator", "adventure", "c", "s", "sp", "a");
        String partial = input.toLowerCase();
        return values.stream().filter(value -> value.startsWith(partial)).toList();
    }

    public String modeName(GameMode mode) {
        return configString("modes." + mode.name().toLowerCase(), mode.name());
    }

    private void notifyStaff(CommandSender actor, Player target, String modeName, boolean self) {
        Component notification = render(
                self ? "messages.staff-notify-self" : "messages.staff-notify-other",
                Map.of(
                        "actor", actor.getName(),
                        "target", target.getName(),
                        "mode", modeName
                )
        );

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission("auriongo.gamemode.notify") && !player.hasPermission("auriongo.gamemode")) {
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
        String template = configString(path, "<color:#FF4F4F>Отсутствует шаблон: " + path);
        String resolved = template;

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolved = resolved.replace("{" + entry.getKey() + "}", this.miniMessage.escapeTags(entry.getValue()));
        }

        return this.miniMessage.deserialize(resolved);
    }

    private Component renderMessages(String path, Map<String, String> placeholders) {
        String template = this.context.configManager().require(StandardConfigs.MESSAGES).configuration().getString(path, "<color:#FF4F4F>Отсутствует шаблон: " + path);
        String resolved = template;

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolved = resolved.replace("{" + entry.getKey() + "}", this.miniMessage.escapeTags(entry.getValue()));
        }

        return this.miniMessage.deserialize(resolved);
    }

    private String configString(String path, String fallback) {
        return this.context.configManager().require(StandardConfigs.GAMEMODES).configuration().getString(path, fallback);
    }
}
