package dev.toxi.aurionGo.storage.punishment;

import java.util.List;

public record PunishmentPage(
        List<PunishmentRecord> entries,
        int page,
        int totalPages,
        int totalEntries
) {
}
