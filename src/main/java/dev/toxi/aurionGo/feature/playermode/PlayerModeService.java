package dev.toxi.aurionGo.feature.playermode;

import dev.toxi.aurionGo.message.MessageFormatter;
import dev.toxi.aurionGo.shared.AurionContext;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public final class PlayerModeService {

    private final MessageFormatter messageFormatter;

    public PlayerModeService(AurionContext context) {
        this.messageFormatter = context.serviceRegistry().require(MessageFormatter.class);
    }

    public void applyGod(CommandSender sender, Player target) {
        boolean newGod = !target.isInvulnerable();
        target.setInvulnerable(newGod);

        boolean self = sender instanceof Player player && player.getUniqueId().equals(target.getUniqueId());
        String state = newGod ? "включён" : "выключен";

        target.sendMessage(render(
                self ? "playermode.god.messages.self-change" : "playermode.god.messages.target-change",
                Map.of("state", state, "target", target.getName(), "actor", sender.getName())
        ));

        if (!self) {
            sender.sendMessage(render(
                    "playermode.god.messages.other-change",
                    Map.of("state", state, "target", target.getName(), "actor", sender.getName())
            ));
        }

        notifyStaff(sender, target, "God", state, self);
    }

    public void applyFly(CommandSender sender, Player target) {
        boolean newFly = !target.getAllowFlight();
        target.setAllowFlight(newFly);
        if (!newFly) {
            target.setFlying(false);
        }

        boolean self = sender instanceof Player player && player.getUniqueId().equals(target.getUniqueId());
        String state = newFly ? "включён" : "выключен";

        target.sendMessage(render(
                self ? "playermode.fly.messages.self-change" : "playermode.fly.messages.target-change",
                Map.of("state", state, "target", target.getName(), "actor", sender.getName())
        ));

        if (!self) {
            sender.sendMessage(render(
                    "playermode.fly.messages.other-change",
                    Map.of("state", state, "target", target.getName(), "actor", sender.getName())
            ));
        }

        notifyStaff(sender, target, "Fly", state, self);
    }

    public Component renderGodUsage() {
        return render("playermode.god.usage", Map.of());
    }

    public Component renderFlyUsage() {
        return render("playermode.fly.usage", Map.of());
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

    private void notifyStaff(CommandSender actor, Player target, String mode, String state, boolean self) {
        Component notification = render(
                self ? "playermode." + mode.toLowerCase() + ".messages.staff-notify-self" : "playermode." + mode.toLowerCase() + ".messages.staff-notify-other",
                Map.of("actor", actor.getName(), "target", target.getName(), "state", state)
        );

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission("auriongo.command.misc.playermode.notify") && !player.hasPermission("auriongo.command.misc.playermode")) {
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