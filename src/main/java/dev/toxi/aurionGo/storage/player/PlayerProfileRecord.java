package dev.toxi.aurionGo.storage.player;

import java.util.UUID;

public record PlayerProfileRecord(
        UUID uuid,
        String nickname,
        String ipAddress,
        long firstJoin,
        long lastJoin,
        boolean banned,
        Long banExpiresAt,
        boolean muted,
        Long muteExpiresAt,
        int activeWarns
) {
}
