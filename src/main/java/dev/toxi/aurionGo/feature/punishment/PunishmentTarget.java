package dev.toxi.aurionGo.feature.punishment;

import org.bukkit.entity.Player;

import java.util.UUID;

public record PunishmentTarget(
        UUID uuid,
        String nickname,
        Player onlinePlayer
) {
}
