package dev.toxi.aurionGo.feature.chat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ReplyTracker {
    private final Map<UUID, UUID> replyTargets = new HashMap<>();

    public void link(UUID first, UUID second) {
        this.replyTargets.put(first, second);
        this.replyTargets.put(second, first);
    }

    public UUID getReplyTarget(UUID playerId) {
        return this.replyTargets.get(playerId);
    }

    public void clear(UUID playerId) {
        this.replyTargets.remove(playerId);
    }

    public void removePlayer(UUID playerId) {
        this.replyTargets.remove(playerId);
        this.replyTargets.entrySet().removeIf(entry -> entry.getValue().equals(playerId));
    }

    public void clearAll() {
        this.replyTargets.clear();
    }
}
