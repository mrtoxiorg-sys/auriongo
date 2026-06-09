package dev.toxi.aurionGo.storage.punishment;

import dev.toxi.aurionGo.feature.punishment.PunishmentType;

import java.util.UUID;

public record PunishmentRecord(
        long id,
        PunishmentType type,
        UUID targetUuid,
        String targetNickname,
        UUID moderatorUuid,
        String moderatorName,
        String reason,
        long createdAt,
        Long expiresAt,
        boolean active
) {
}
