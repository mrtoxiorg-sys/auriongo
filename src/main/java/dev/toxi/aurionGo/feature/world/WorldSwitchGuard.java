package dev.toxi.aurionGo.feature.world;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class WorldSwitchGuard {

    private final WorldSettings settings;
    private final ConcurrentMap<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> combatBlocks = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> pending = new ConcurrentHashMap<>();

    WorldSwitchGuard(WorldSettings settings) {
        this.settings = settings;
    }

    void markCombat(UUID uniqueId) {
        if (this.settings.combatBlockMillis() <= 0L) {
            return;
        }

        this.combatBlocks.put(
            uniqueId,
            System.currentTimeMillis() + this.settings.combatBlockMillis()
        );
    }

    void markCooldown(UUID uniqueId) {
        this.cooldowns.put(
            uniqueId,
            System.currentTimeMillis() + this.settings.cooldownMillis()
        );
    }

    void markPending(UUID uniqueId) {
        this.pending.put(
            uniqueId,
            System.currentTimeMillis() + this.settings.lookupTimeoutMillis()
        );
    }

    boolean consumePending(UUID uniqueId) {
        return this.pending.remove(uniqueId) != null;
    }

    boolean isPending(UUID uniqueId, long now) {
        Long expiresAt = this.pending.get(uniqueId);

        if (expiresAt == null) {
            return false;
        }

        if (expiresAt > now) {
            return true;
        }

        this.pending.remove(uniqueId, expiresAt);
        return false;
    }

    long combatRemaining(UUID uniqueId, long now) {
        return remaining(this.combatBlocks.get(uniqueId), now);
    }

    long cooldownRemaining(UUID uniqueId, long now) {
        return remaining(this.cooldowns.get(uniqueId), now);
    }

    void forget(UUID uniqueId) {
        this.cooldowns.remove(uniqueId);
        this.combatBlocks.remove(uniqueId);
        this.pending.remove(uniqueId);
    }

    void clear() {
        this.cooldowns.clear();
        this.combatBlocks.clear();
        this.pending.clear();
    }

    private long remaining(Long until, long now) {
        if (until == null || until <= now) {
            return 0L;
        }

        return Math.max(1L, (until - now + 999L) / 1000L);
    }
}
