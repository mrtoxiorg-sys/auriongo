package dev.toxi.aurionGo.storage.punishment;

import dev.toxi.aurionGo.feature.punishment.PunishmentType;
import dev.toxi.aurionGo.storage.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class PunishmentRepository {
    private final DatabaseManager databaseManager;

    public PunishmentRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public PunishmentRecord create(PunishmentCreateRequest request) throws SQLException {
        String sql = """
                INSERT INTO aurion_punishments
                (type, target_uuid, target_nickname, moderator_uuid, moderator_name, reason, created_at, expires_at, active, removed_at, removed_by_uuid, removed_by_name, removal_reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL, NULL, NULL)
                """;

        try (Connection connection = this.databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, request.type().name());
            statement.setString(2, request.targetUuid().toString());
            statement.setString(3, request.targetNickname());

            if (request.moderatorUuid() == null) {
                statement.setNull(4, Types.VARCHAR);
            } else {
                statement.setString(4, request.moderatorUuid().toString());
            }

            statement.setString(5, request.moderatorName());
            statement.setString(6, request.reason());
            statement.setLong(7, request.createdAt());

            if (request.expiresAt() == null) {
                statement.setNull(8, Types.BIGINT);
            } else {
                statement.setLong(8, request.expiresAt());
            }

            statement.setBoolean(9, request.active());
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Не удалось получить ID наказания.");
                }

                return new PunishmentRecord(
                        keys.getLong(1),
                        request.type(),
                        request.targetUuid(),
                        request.targetNickname(),
                        request.moderatorUuid(),
                        request.moderatorName(),
                        request.reason(),
                        request.createdAt(),
                        request.expiresAt(),
                        request.active()
                );
            }
        }
    }

    public Optional<PunishmentRecord> findActiveByTypeAndTarget(PunishmentType type, UUID targetUuid, long now) throws SQLException {
        String sql = """
                SELECT * FROM aurion_punishments
                WHERE type = ? AND target_uuid = ? AND active = TRUE
                  AND (expires_at IS NULL OR expires_at > ?)
                ORDER BY created_at DESC
                LIMIT 1
                """;

        try (Connection connection = this.databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type.name());
            statement.setString(2, targetUuid.toString());
            statement.setLong(3, now);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(map(resultSet));
            }
        }
    }

    public Optional<PunishmentRecord> findById(long id) throws SQLException {
        String sql = "SELECT * FROM aurion_punishments WHERE id = ? LIMIT 1";

        try (Connection connection = this.databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(map(resultSet));
            }
        }
    }

    public int deactivateExpired(PunishmentType type, UUID targetUuid, long now) throws SQLException {
        String sql = """
                UPDATE aurion_punishments
                SET active = FALSE, removed_at = ?, removed_by_name = ?, removal_reason = ?
                WHERE type = ? AND target_uuid = ? AND active = TRUE
                  AND expires_at IS NOT NULL AND expires_at <= ?
                """;

        try (Connection connection = this.databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, now);
            statement.setString(2, "SYSTEM");
            statement.setString(3, "Истек срок наказания");
            statement.setString(4, type.name());
            statement.setString(5, targetUuid.toString());
            statement.setLong(6, now);
            return statement.executeUpdate();
        }
    }

    public int countActiveForTarget(PunishmentType type, UUID targetUuid, long now) throws SQLException {
        String sql = """
                SELECT COUNT(*) FROM aurion_punishments
                WHERE type = ? AND target_uuid = ? AND active = TRUE
                  AND (expires_at IS NULL OR expires_at > ?)
                """;

        try (Connection connection = this.databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type.name());
            statement.setString(2, targetUuid.toString());
            statement.setLong(3, now);

            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    public boolean deactivateById(PunishmentType type, long id, UUID moderatorUuid, String moderatorName, String reason, long removedAt) throws SQLException {
        String sql = """
                UPDATE aurion_punishments
                SET active = FALSE, removed_at = ?, removed_by_uuid = ?, removed_by_name = ?, removal_reason = ?
                WHERE id = ? AND type = ? AND active = TRUE
                """;

        try (Connection connection = this.databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, removedAt);

            if (moderatorUuid == null) {
                statement.setNull(2, Types.VARCHAR);
            } else {
                statement.setString(2, moderatorUuid.toString());
            }

            statement.setString(3, moderatorName);
            statement.setString(4, reason);
            statement.setLong(5, id);
            statement.setString(6, type.name());
            return statement.executeUpdate() > 0;
        }
    }

    public boolean deactivateLatestByTarget(PunishmentType type, UUID targetUuid, UUID moderatorUuid, String moderatorName, String reason, long removedAt, long now) throws SQLException {
        Optional<PunishmentRecord> active = findActiveByTypeAndTarget(type, targetUuid, now);

        if (active.isEmpty()) {
            return false;
        }

        return deactivateById(type, active.get().id(), moderatorUuid, moderatorName, reason, removedAt);
    }

    public PunishmentPage listActive(PunishmentType type, int page, int pageSize, long now) throws SQLException {
        int totalEntries = countActive(type, now);
        int totalPages = Math.max(1, (int) Math.ceil(totalEntries / (double) pageSize));
        int safePage = Math.min(Math.max(page, 1), totalPages);
        int offset = (safePage - 1) * pageSize;

        String sql = """
                SELECT * FROM aurion_punishments
                WHERE type = ? AND active = TRUE AND (expires_at IS NULL OR expires_at > ?)
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
                """;

        List<PunishmentRecord> entries = new ArrayList<>();

        try (Connection connection = this.databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type.name());
            statement.setLong(2, now);
            statement.setInt(3, pageSize);
            statement.setInt(4, offset);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    entries.add(map(resultSet));
                }
            }
        }

        return new PunishmentPage(entries, safePage, totalPages, totalEntries);
    }

    public List<String> findActiveTargetNames(PunishmentType type, String prefix, int limit, long now) throws SQLException {
        String sql = """
                SELECT DISTINCT target_nickname
                FROM aurion_punishments
                WHERE type = ? AND active = TRUE
                  AND (expires_at IS NULL OR expires_at > ?)
                  AND LOWER(target_nickname) LIKE LOWER(?)
                ORDER BY target_nickname ASC
                LIMIT ?
                """;

        List<String> results = new ArrayList<>();

        try (Connection connection = this.databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type.name());
            statement.setLong(2, now);
            statement.setString(3, prefix + "%");
            statement.setInt(4, limit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    results.add(resultSet.getString(1));
                }
            }
        }

        return results;
    }

    public PunishmentPage searchByNickname(PunishmentType type, String query, int page, int pageSize, long now) throws SQLException {
        int totalEntries = countByNickname(type, query, now);
        int totalPages = Math.max(1, (int) Math.ceil(totalEntries / (double) pageSize));
        int safePage = Math.min(Math.max(page, 1), totalPages);
        int offset = (safePage - 1) * pageSize;

        String sql = """
                SELECT * FROM aurion_punishments
                WHERE type = ? AND active = TRUE
                  AND (expires_at IS NULL OR expires_at > ?)
                  AND LOWER(target_nickname) LIKE LOWER(?)
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
                """;

        List<PunishmentRecord> entries = new ArrayList<>();

        try (Connection connection = this.databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type.name());
            statement.setLong(2, now);
            statement.setString(3, "%" + query + "%");
            statement.setInt(4, pageSize);
            statement.setInt(5, offset);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    entries.add(map(resultSet));
                }
            }
        }

        return new PunishmentPage(entries, safePage, totalPages, totalEntries);
    }

    public int countTotalForTarget(PunishmentType type, UUID targetUuid) throws SQLException {
        String sql = """
                SELECT COUNT(*) FROM aurion_punishments
                WHERE type = ? AND target_uuid = ?
                """;

        try (Connection connection = this.databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type.name());
            statement.setString(2, targetUuid.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private int countByNickname(PunishmentType type, String query, long now) throws SQLException {
        String sql = """
                SELECT COUNT(*) FROM aurion_punishments
                WHERE type = ? AND active = TRUE
                  AND (expires_at IS NULL OR expires_at > ?)
                  AND LOWER(target_nickname) LIKE LOWER(?)
                """;

        try (Connection connection = this.databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type.name());
            statement.setLong(2, now);
            statement.setString(3, "%" + query + "%");

            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private int countActive(PunishmentType type, long now) throws SQLException {
        String sql = """
                SELECT COUNT(*) FROM aurion_punishments
                WHERE type = ? AND active = TRUE AND (expires_at IS NULL OR expires_at > ?)
                """;

        try (Connection connection = this.databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type.name());
            statement.setLong(2, now);

            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private PunishmentRecord map(ResultSet resultSet) throws SQLException {
        String moderatorUuidRaw = resultSet.getString("moderator_uuid");
        long expiresAt = resultSet.getLong("expires_at");

        return new PunishmentRecord(
                resultSet.getLong("id"),
                PunishmentType.valueOf(resultSet.getString("type")),
                UUID.fromString(resultSet.getString("target_uuid")),
                resultSet.getString("target_nickname"),
                moderatorUuidRaw == null ? null : UUID.fromString(moderatorUuidRaw),
                resultSet.getString("moderator_name"),
                resultSet.getString("reason"),
                resultSet.getLong("created_at"),
                resultSet.wasNull() ? null : expiresAt,
                resultSet.getBoolean("active")
        );
    }
}
