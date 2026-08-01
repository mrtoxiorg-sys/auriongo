package dev.toxi.aurionGo.storage;

import dev.toxi.aurionGo.config.ConfigFile;
import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseManager implements AutoCloseable {

    private final ConfigFile configFile;
    private DatabaseType type;
    private String jdbcUrl;
    private String username;
    private String password;

    public DatabaseManager(ConfigFile configFile) {
        this.configFile = configFile;
    }

    public void initialize() {
        this.type = DatabaseType.fromConfig(
            this.configFile.configuration().getString("storage.type", "SQLITE")
        );
        this.jdbcUrl = buildJdbcUrl(this.type);
        this.username = this.configFile
            .configuration()
            .getString("storage." + this.type.configKey() + ".username", "");
        this.password = this.configFile
            .configuration()
            .getString("storage." + this.type.configKey() + ".password", "");

        try {
            Class.forName(this.type.driverClassName());
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException(
                "Не удалось загрузить JDBC-драйвер " +
                    this.type.driverClassName(),
                exception
            );
        }

        createSchema();
    }

    public Connection getConnection() throws SQLException {
        return switch (this.type) {
            case SQLITE -> DriverManager.getConnection(this.jdbcUrl);
            case MYSQL, MARIADB -> DriverManager.getConnection(
                this.jdbcUrl,
                this.username,
                this.password
            );
        };
    }

    @Override
    public void close() {
        // DriverManager-based access does not require an explicit shutdown hook.
    }

    private void createSchema() {
        try (
            Connection connection = getConnection();
            Statement statement = connection.createStatement()
        ) {
            statement.execute(playerTableSql());
            ensurePlayerColumns(connection, statement);
            statement.execute(playerIpHistoryTableSql());
            statement.execute(punishmentsTableSql());
        } catch (SQLException exception) {
            throw new IllegalStateException(
                "Не удалось создать схему базы данных AurionGo.",
                exception
            );
        }
    }

    private String buildJdbcUrl(DatabaseType databaseType) {
        return switch (databaseType) {
            case SQLITE -> buildSqliteUrl();
            case MYSQL -> buildRemoteUrl("mysql");
            case MARIADB -> buildRemoteUrl("mariadb");
        };
    }

    private String buildSqliteUrl() {
        String configuredPath = this.configFile
            .configuration()
            .getString("storage.sqlite.file", "database/auriongo.db");
        File databaseFile = new File(configuredPath);

        if (!databaseFile.isAbsolute()) {
            File pluginDirectory = this.configFile.file().getParentFile();
            databaseFile = new File(pluginDirectory, configuredPath);
        }

        File parent = databaseFile.getParentFile();

        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        return "jdbc:sqlite:" + databaseFile.getAbsolutePath();
    }

    private String buildRemoteUrl(String driver) {
        String key = "storage." + this.type.configKey();
        String host = this.configFile
            .configuration()
            .getString(key + ".host", "127.0.0.1");
        int port = this.configFile.configuration().getInt(key + ".port", 3306);
        String database = this.configFile
            .configuration()
            .getString(key + ".database", "auriongo");
        String parameters = this.configFile
            .configuration()
            .getString(key + ".parameters", "");
        String suffix = parameters.isBlank() ? "" : "?" + parameters;
        return (
            "jdbc:" +
            driver +
            "://" +
            host +
            ":" +
            port +
            "/" +
            database +
            suffix
        );
    }

    private String playerTableSql() {
        return """
        CREATE TABLE IF NOT EXISTS aurion_players (
            uuid VARCHAR(36) PRIMARY KEY,
            nickname VARCHAR(16) NOT NULL,
            ip_address VARCHAR(45) NOT NULL,
            first_join BIGINT NOT NULL,
            last_join BIGINT NOT NULL,
            banned BOOLEAN NOT NULL DEFAULT FALSE,
            ban_expires_at BIGINT NULL,
            muted BOOLEAN NOT NULL DEFAULT FALSE,
            mute_expires_at BIGINT NULL,
            active_warns INTEGER NOT NULL DEFAULT 0,
            hide_join_leave_messages BOOLEAN NOT NULL DEFAULT FALSE,
            hide_afk_messages BOOLEAN NOT NULL DEFAULT FALSE,
            spy_enabled BOOLEAN NOT NULL DEFAULT FALSE,
            hide_nametag BOOLEAN NOT NULL DEFAULT FALSE,
            suppress_join_quit_until BIGINT NULL
        )
        """;
    }

    private String punishmentsTableSql() {
        return switch (this.type) {
            case SQLITE -> """
            CREATE TABLE IF NOT EXISTS aurion_punishments (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                type VARCHAR(16) NOT NULL,
                target_uuid VARCHAR(36) NOT NULL,
                target_nickname VARCHAR(16) NOT NULL,
                moderator_uuid VARCHAR(36) NULL,
                moderator_name VARCHAR(16) NOT NULL,
                reason TEXT NOT NULL,
                created_at BIGINT NOT NULL,
                expires_at BIGINT NULL,
                active BOOLEAN NOT NULL,
                removed_at BIGINT NULL,
                removed_by_uuid VARCHAR(36) NULL,
                removed_by_name VARCHAR(16) NULL,
                removal_reason TEXT NULL
            )
            """;
            case MYSQL, MARIADB -> """
            CREATE TABLE IF NOT EXISTS aurion_punishments (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                type VARCHAR(16) NOT NULL,
                target_uuid VARCHAR(36) NOT NULL,
                target_nickname VARCHAR(16) NOT NULL,
                moderator_uuid VARCHAR(36) NULL,
                moderator_name VARCHAR(16) NOT NULL,
                reason TEXT NOT NULL,
                created_at BIGINT NOT NULL,
                expires_at BIGINT NULL,
                active BOOLEAN NOT NULL,
                removed_at BIGINT NULL,
                removed_by_uuid VARCHAR(36) NULL,
                removed_by_name VARCHAR(16) NULL,
                removal_reason TEXT NULL
            )
            """;
        };
    }

    private String playerIpHistoryTableSql() {
        return """
        CREATE TABLE IF NOT EXISTS aurion_player_ip_history (
            player_uuid VARCHAR(36) NOT NULL,
            ip_address VARCHAR(45) NOT NULL,
            first_seen BIGINT NOT NULL,
            last_seen BIGINT NOT NULL,
            PRIMARY KEY (player_uuid, ip_address)
        )
        """;
    }

    private void ensurePlayerColumns(Connection connection, Statement statement)
        throws SQLException {
        ensureColumn(
            connection,
            statement,
            "aurion_players",
            "banned",
            "BOOLEAN NOT NULL DEFAULT FALSE"
        );
        ensureColumn(
            connection,
            statement,
            "aurion_players",
            "ban_expires_at",
            "BIGINT NULL"
        );
        ensureColumn(
            connection,
            statement,
            "aurion_players",
            "muted",
            "BOOLEAN NOT NULL DEFAULT FALSE"
        );
        ensureColumn(
            connection,
            statement,
            "aurion_players",
            "mute_expires_at",
            "BIGINT NULL"
        );
        ensureColumn(
            connection,
            statement,
            "aurion_players",
            "active_warns",
            "INTEGER NOT NULL DEFAULT 0"
        );
        ensureColumn(
            connection,
            statement,
            "aurion_players",
            "hide_join_leave_messages",
            "BOOLEAN NOT NULL DEFAULT FALSE"
        );
        ensureColumn(
            connection,
            statement,
            "aurion_players",
            "hide_afk_messages",
            "BOOLEAN NOT NULL DEFAULT FALSE"
        );
        ensureColumn(
            connection,
            statement,
            "aurion_players",
            "spy_enabled",
            "BOOLEAN NOT NULL DEFAULT FALSE"
        );
        ensureColumn(
            connection,
            statement,
            "aurion_players",
            "hide_nametag",
            "BOOLEAN NOT NULL DEFAULT FALSE"
        );
        ensureColumn(
            connection,
            statement,
            "aurion_players",
            "suppress_join_quit_until",
            "BIGINT NULL"
        );
    }

    private void ensureColumn(
        Connection connection,
        Statement statement,
        String tableName,
        String columnName,
        String definition
    ) throws SQLException {
        if (columnExists(connection, tableName, columnName)) {
            return;
        }

        statement.execute(
            "ALTER TABLE " +
                tableName +
                " ADD COLUMN " +
                columnName +
                " " +
                definition
        );
    }

    private boolean columnExists(
        Connection connection,
        String tableName,
        String columnName
    ) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        return (
            hasColumn(metadata, tableName, columnName) ||
            hasColumn(
                metadata,
                tableName.toUpperCase(),
                columnName.toUpperCase()
            ) ||
            hasColumn(
                metadata,
                tableName.toLowerCase(),
                columnName.toLowerCase()
            )
        );
    }

    private boolean hasColumn(
        DatabaseMetaData metadata,
        String tableName,
        String columnName
    ) throws SQLException {
        try (
            var resultSet = metadata.getColumns(
                null,
                null,
                tableName,
                columnName
            )
        ) {
            return resultSet.next();
        }
    }
}
