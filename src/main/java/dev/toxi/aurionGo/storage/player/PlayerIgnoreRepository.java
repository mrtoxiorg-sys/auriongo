package dev.toxi.aurionGo.storage.player;

import dev.toxi.aurionGo.storage.DatabaseManager;
import dev.toxi.aurionGo.storage.DatabaseType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerIgnoreRepository {

    private final DatabaseManager databaseManager;

    public PlayerIgnoreRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public Map<UUID, String> findAllByPlayer(UUID playerUuid)
        throws SQLException {
        String sql = """
            SELECT ignored_uuid, ignored_nickname
            FROM aurion_player_ignores
            WHERE player_uuid = ?
            """;
        Map<UUID, String> entries = new LinkedHashMap<>();

        try (
            Connection connection = this.databaseManager.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, playerUuid.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    entries.put(
                        UUID.fromString(resultSet.getString("ignored_uuid")),
                        resultSet.getString("ignored_nickname")
                    );
                }
            }
        }

        return entries;
    }

    public boolean insert(
        UUID playerUuid,
        UUID ignoredUuid,
        String ignoredNickname
    ) throws SQLException {
        try (
            Connection connection = this.databaseManager.getConnection();
            PreparedStatement statement = connection.prepareStatement(insertSql())
        ) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, ignoredUuid.toString());
            statement.setString(3, ignoredNickname);
            statement.setLong(4, System.currentTimeMillis());
            return statement.executeUpdate() > 0;
        }
    }

    public int delete(UUID playerUuid, UUID ignoredUuid)
        throws SQLException {
        String sql = """
            DELETE FROM aurion_player_ignores
            WHERE player_uuid = ?
              AND ignored_uuid = ?
            """;

        try (
            Connection connection = this.databaseManager.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, ignoredUuid.toString());
            return statement.executeUpdate();
        }
    }

    private String insertSql() {
        if (this.databaseManager.type() == DatabaseType.SQLITE) {
            return """
                INSERT OR IGNORE INTO aurion_player_ignores
                (player_uuid, ignored_uuid, ignored_nickname, created_at)
                VALUES (?, ?, ?, ?)
                """;
        }

        return """
            INSERT IGNORE INTO aurion_player_ignores
            (player_uuid, ignored_uuid, ignored_nickname, created_at)
            VALUES (?, ?, ?, ?)
            """;
    }
}
