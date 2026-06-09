package dev.toxi.aurionGo.storage.player;

import dev.toxi.aurionGo.storage.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public final class PlayerProfileRepository {
    private final DatabaseManager databaseManager;

    public PlayerProfileRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public boolean saveOrUpdateJoin(PlayerProfileSnapshot snapshot) throws SQLException {
        Long existingFirstJoin = findFirstJoin(snapshot.uuid().toString());

        if (existingFirstJoin == null) {
            insert(snapshot);
            return true;
        }

        update(snapshot, existingFirstJoin);
        return false;
    }

    public Optional<PlayerProfileRecord> findByNickname(String nickname) throws SQLException {
        String sql = """
                SELECT uuid, nickname, ip_address, first_join, last_join
                FROM aurion_players
                WHERE LOWER(nickname) = LOWER(?)
                LIMIT 1
                """;

        try (Connection connection = this.databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, nickname);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(new PlayerProfileRecord(
                        UUID.fromString(resultSet.getString("uuid")),
                        resultSet.getString("nickname"),
                        resultSet.getString("ip_address"),
                        resultSet.getLong("first_join"),
                        resultSet.getLong("last_join")
                ));
            }
        }
    }

    private Long findFirstJoin(String uuid) throws SQLException {
        String sql = "SELECT first_join FROM aurion_players WHERE uuid = ?";

        try (Connection connection = this.databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }

                return resultSet.getLong("first_join");
            }
        }
    }

    private void insert(PlayerProfileSnapshot snapshot) throws SQLException {
        String sql = """
                INSERT INTO aurion_players (uuid, nickname, ip_address, first_join, last_join)
                VALUES (?, ?, ?, ?, ?)
                """;

        try (Connection connection = this.databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, snapshot.uuid().toString());
            statement.setString(2, snapshot.nickname());
            statement.setString(3, snapshot.ipAddress());
            statement.setLong(4, snapshot.firstJoin());
            statement.setLong(5, snapshot.lastJoin());
            statement.executeUpdate();
        }
    }

    private void update(PlayerProfileSnapshot snapshot, long firstJoin) throws SQLException {
        String sql = """
                UPDATE aurion_players
                SET nickname = ?, ip_address = ?, first_join = ?, last_join = ?
                WHERE uuid = ?
                """;

        try (Connection connection = this.databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, snapshot.nickname());
            statement.setString(2, snapshot.ipAddress());
            statement.setLong(3, firstJoin);
            statement.setLong(4, snapshot.lastJoin());
            statement.setString(5, snapshot.uuid().toString());
            statement.executeUpdate();
        }
    }
}
