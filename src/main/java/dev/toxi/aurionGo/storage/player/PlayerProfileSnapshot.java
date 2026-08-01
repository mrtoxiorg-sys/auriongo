package dev.toxi.aurionGo.storage.player;

import java.util.UUID;

public record PlayerProfileSnapshot(
        UUID uuid,
        String nickname,
        String ipAddress,
        long firstJoin,
        long lastJoin
) {
}
