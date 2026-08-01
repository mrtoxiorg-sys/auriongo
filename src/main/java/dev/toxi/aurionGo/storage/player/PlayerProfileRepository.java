package dev.toxi.aurionGo.storage.player;

import dev.toxi.aurionGo.feature.player.HideSettings;
import dev.toxi.aurionGo.storage.DatabaseManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class PlayerProfileRepository {

    private final DatabaseManager databaseManager;

    public PlayerProfileRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public boolean saveOrUpdateJoin(PlayerProfileSnapshot snapshot)
        throws SQLException {
        Long existingFirstJoin = findFirstJoin(snapshot.uuid().toString());

        if (existingFirstJoin == null) {
            insert(snapshot);
            return true;
        }

        update(snapshot, existingFirstJoin);
        return false;
    }

    public void saveOrUpdateIpHistory(PlayerProfileSnapshot snapshot)
        throws SQLException {
        Long existingFirstSeen = findIpHistoryFirstSeen(
            snapshot.uuid().toString(),
            snapshot.ipAddress()
        );

        if (existingFirstSeen == null) {
            insertIpHistory(snapshot);
            return;
        }

        updateIpHistory(snapshot, existingFirstSeen);
    }

    public Optional<PlayerProfileRecord> findByNickname(String nickname)
        throws SQLException {
        String sql = """
            SELECT uuid, nickname, ip_address, first_join, last_join, banned, ban_expires_at, muted, mute_expires_at, active_warns, hide_join_leave_messages, hide_afk_messages, spy_enabled, hide_nametag, suppress_join_quit_until
            FROM aurion_players
            WHERE LOWER(nickname) = LOWER(?)
            LIMIT 1
            """;

        try (
            Connection connection = this.databaseManager.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, nickname);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(
                    new PlayerProfileRecord(
                        UUID.fromString(resultSet.getString("uuid")),
                        resultSet.getString("nickname"),
                        resultSet.getString("ip_address"),
                        resultSet.getLong("first_join"),
                        resultSet.getLong("last_join"),
                        resultSet.getBoolean("banned"),
                        getNullableLong(resultSet, "ban_expires_at"),
                        resultSet.getBoolean("muted"),
                        getNullableLong(resultSet, "mute_expires_at"),
                        resultSet.getInt("active_warns"),
                        resultSet.getBoolean("hide_join_leave_messages"),
                        resultSet.getBoolean("hide_afk_messages"),
                        resultSet.getBoolean("spy_enabled"),
                        resultSet.getBoolean("hide_nametag"),
                        getNullableLong(resultSet, "suppress_join_quit_until")
                    )
                );
            }
        }
    }

    public Optional<PlayerProfileRecord> findByUuid(UUID uuid) throws SQLException {
        String sql = """
            SELECT uuid, nickname, ip_address, first_join, last_join, banned, ban_expires_at, muted, mute_expires_at, active_warns, hide_join_leave_messages, hide_afk_messages, spy_enabled, hide_nametag, suppress_join_quit_until
            FROM aurion_players
            WHERE uuid = ?
            LIMIT 1
            """;

        try (
            Connection connection = this.databaseManager.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, uuid.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(
                    new PlayerProfileRecord(
                        UUID.fromString(resultSet.getString("uuid")),
                        resultSet.getString("nickname"),
                        resultSet.getString("ip_address"),
                        resultSet.getLong("first_join"),
                        resultSet.getLong("last_join"),
                        resultSet.getBoolean("banned"),
                        getNullableLong(resultSet, "ban_expires_at"),
                        resultSet.getBoolean("muted"),
                        getNullableLong(resultSet, "mute_expires_at"),
                        resultSet.getInt("active_warns"),
                        resultSet.getBoolean("hide_join_leave_messages"),
                        resultSet.getBoolean("hide_afk_messages"),
                        resultSet.getBoolean("spy_enabled"),
                        resultSet.getBoolean("hide_nametag"),
                        getNullableLong(resultSet, "suppress_join_quit_until")
                    )
                );
            }
        }
    }

    public List<PlayerProfileRecord> findByIpAddress(String ipAddress)
        throws SQLException {
        String sql = """
            SELECT uuid, nickname, ip_address, first_join, last_join, banned, ban_expires_at, muted, mute_expires_at, active_warns, hide_join_leave_messages, hide_afk_messages, spy_enabled, hide_nametag, suppress_join_quit_until
            FROM aurion_players
            WHERE uuid IN (
                SELECT player_uuid
                FROM aurion_player_ip_history
                WHERE ip_address = ?
            )
            ORDER BY last_join DESC, LOWER(nickname) ASC
            """;

        try (
            Connection connection = this.databaseManager.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, ipAddress);

            try (ResultSet resultSet = statement.executeQuery()) {
                List<PlayerProfileRecord> profiles = new ArrayList<>();

                while (resultSet.next()) {
                    profiles.add(
                        new PlayerProfileRecord(
                            UUID.fromString(resultSet.getString("uuid")),
                            resultSet.getString("nickname"),
                            resultSet.getString("ip_address"),
                            resultSet.getLong("first_join"),
                            resultSet.getLong("last_join"),
                            resultSet.getBoolean("banned"),
                            getNullableLong(resultSet, "ban_expires_at"),
                            resultSet.getBoolean("muted"),
                            getNullableLong(resultSet, "mute_expires_at"),
                            resultSet.getInt("active_warns"),
                            resultSet.getBoolean("hide_join_leave_messages"),
                            resultSet.getBoolean("hide_afk_messages"),
                            resultSet.getBoolean("spy_enabled"),
                            resultSet.getBoolean("hide_nametag"),
                            getNullableLong(resultSet, "suppress_join_quit_until")
                        )
                    );
                }

                return profiles;
            }
        }
    }

    public void updateBanState(UUID uuid, boolean banned, Long expiresAt)
        throws SQLException {
        String sql = """
            UPDATE aurion_players
            SET banned = ?, ban_expires_at = ?
            WHERE uuid = ?
            """;

        try (
            Connection connection = this.databaseManager.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setBoolean(1, banned);

            if (expiresAt == null) {
                statement.setNull(2, java.sql.Types.BIGINT);
            } else {
                statement.setLong(2, expiresAt);
            }

            statement.setString(3, uuid.toString());
            statement.executeUpdate();
        }
    }

    public void updateMuteState(UUID uuid, boolean muted, Long expiresAt)
        throws SQLException {
        String sql = """
            UPDATE aurion_players
            SET muted = ?, mute_expires_at = ?
            WHERE uuid = ?
            """;

        try (
            Connection connection = this.databaseManager.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setBoolean(1, muted);

            if (expiresAt == null) {
                statement.setNull(2, java.sql.Types.BIGINT);
            } else {
                statement.setLong(2, expiresAt);
            }

            statement.setString(3, uuid.toString());
            statement.executeUpdate();
        }
    }

    public void updateWarnCount(UUID uuid, int activeWarns)
        throws SQLException {
        String sql = """
            UPDATE aurion_players
            SET active_warns = ?
            WHERE uuid = ?
            """;

        try (
            Connection connection = this.databaseManager.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setInt(1, activeWarns);
            statement.setString(2, uuid.toString());
            statement.executeUpdate();
        }
    }

    public void updateHideSettings(UUID uuid, HideSettings settings)
        throws SQLException {
        String sql = """
            UPDATE aurion_players
            SET hide_join_leave_messages = ?, hide_afk_messages = ?, spy_enabled = ?, hide_nametag = ?
            WHERE uuid = ?
            """;

        try (
            Connection connection = this.databaseManager.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setBoolean(1, settings.hideJoinLeaveMessages());
            statement.setBoolean(2, settings.hideAfkMessages());
            statement.setBoolean(3, settings.spyEnabled());
            statement.setBoolean(4, settings.hideNametag());
            statement.setString(5, uuid.toString());
            statement.executeUpdate();
        }
    }

    public void updateSuppressJoinQuitUntil(UUID uuid, Long suppressUntil)
        throws SQLException {
        String sql = """
            UPDATE aurion_players
            SET suppress_join_quit_until = ?
            WHERE uuid = ?
            """;

        try (
            Connection connection = this.databaseManager.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            if (suppressUntil == null) {
                statement.setNull(1, java.sql.Types.BIGINT);
            } else {
                statement.setLong(1, suppressUntil);
            }

            statement.setString(2, uuid.toString());
            statement.executeUpdate();
        }
    }

    private Long findFirstJoin(String uuid) throws SQLException {
        String sql = "SELECT first_join FROM aurion_players WHERE uuid = ?";

        try (
            Connection connection = this.databaseManager.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, uuid);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }

                return resultSet.getLong("first_join");
            }
        }
    }

    private Long findIpHistoryFirstSeen(String uuid, String ipAddress)
        throws SQLException {
        String sql = """
            SELECT first_seen
            FROM aurion_player_ip_history
            WHERE player_uuid = ? AND ip_address = ?
            """;

        try (
            Connection connection = this.databaseManager.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, uuid);
            statement.setString(2, ipAddress);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }

                return resultSet.getLong("first_seen");
            }
        }
    }

    private void insert(PlayerProfileSnapshot snapshot) throws SQLException {
        String sql = """
            INSERT INTO aurion_players (uuid, nickname, ip_address, first_join, last_join, banned, ban_expires_at, muted, mute_expires_at, active_warns, hide_join_leave_messages, hide_afk_messages, spy_enabled, hide_nametag, suppress_join_quit_until)
            VALUES (?, ?, ?, ?, ?, FALSE, NULL, FALSE, NULL, 0, FALSE, FALSE, FALSE, FALSE, NULL)
            """;

        try (
            Connection connection = this.databaseManager.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, snapshot.uuid().toString());
            statement.setString(2, snapshot.nickname());
            statement.setString(3, snapshot.ipAddress());
            statement.setLong(4, snapshot.firstJoin());
            statement.setLong(5, snapshot.lastJoin());
            statement.executeUpdate();
        }
    }

    private void update(PlayerProfileSnapshot snapshot, long firstJoin)
        throws SQLException {
        String sql = """
            UPDATE aurion_players
            SET nickname = ?, ip_address = ?, first_join = ?, last_join = ?
            WHERE uuid = ?
            """;

        try (
            Connection connection = this.databaseManager.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, snapshot.nickname());
            statement.setString(2, snapshot.ipAddress());
            statement.setLong(3, firstJoin);
            statement.setLong(4, snapshot.lastJoin());
            statement.setString(5, snapshot.uuid().toString());
            statement.executeUpdate();
        }
    }

    private void insertIpHistory(PlayerProfileSnapshot snapshot) throws SQLException {
        String sql = """
            INSERT INTO aurion_player_ip_history (player_uuid, ip_address, first_seen, last_seen)
            VALUES (?, ?, ?, ?)
            """;

        try (
            Connection connection = this.databaseManager.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, snapshot.uuid().toString());
            statement.setString(2, snapshot.ipAddress());
            statement.setLong(3, snapshot.firstJoin());
            statement.setLong(4, snapshot.lastJoin());
            statement.executeUpdate();
        }
    }

    private void updateIpHistory(PlayerProfileSnapshot snapshot, long firstSeen)
        throws SQLException {
        String sql = """
            UPDATE aurion_player_ip_history
            SET first_seen = ?, last_seen = ?
            WHERE player_uuid = ? AND ip_address = ?
            """;

        try (
            Connection connection = this.databaseManager.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setLong(1, firstSeen);
            statement.setLong(2, snapshot.lastJoin());
            statement.setString(3, snapshot.uuid().toString());
            statement.setString(4, snapshot.ipAddress());
            statement.executeUpdate();
        }
    }

    private Long getNullableLong(ResultSet resultSet, String columnName)
        throws SQLException {
        long value = resultSet.getLong(columnName);
        return resultSet.wasNull() ? null : value;
    }
}
