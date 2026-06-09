package dev.toxi.aurionGo.storage;

import dev.toxi.aurionGo.config.ConfigFile;

import java.io.File;
import java.sql.Connection;
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
        this.type = DatabaseType.fromConfig(this.configFile.configuration().getString("storage.type", "SQLITE"));
        this.jdbcUrl = buildJdbcUrl(this.type);
        this.username = this.configFile.configuration().getString("storage." + this.type.configKey() + ".username", "");
        this.password = this.configFile.configuration().getString("storage." + this.type.configKey() + ".password", "");

        try {
            Class.forName(this.type.driverClassName());
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Не удалось загрузить JDBC-драйвер " + this.type.driverClassName(), exception);
        }

        createSchema();
    }

    public Connection getConnection() throws SQLException {
        return switch (this.type) {
            case SQLITE -> DriverManager.getConnection(this.jdbcUrl);
            case MYSQL, MARIADB -> DriverManager.getConnection(this.jdbcUrl, this.username, this.password);
        };
    }

    @Override
    public void close() {
        // DriverManager-based access does not require an explicit shutdown hook.
    }

    private void createSchema() {
        String sql = """
                CREATE TABLE IF NOT EXISTS aurion_players (
                    uuid VARCHAR(36) PRIMARY KEY,
                    nickname VARCHAR(16) NOT NULL,
                    ip_address VARCHAR(45) NOT NULL,
                    first_join BIGINT NOT NULL,
                    last_join BIGINT NOT NULL
                )
                """;

        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException exception) {
            throw new IllegalStateException("Не удалось создать таблицу aurion_players.", exception);
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
        String configuredPath = this.configFile.configuration().getString("storage.sqlite.file", "database/auriongo.db");
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
        String host = this.configFile.configuration().getString(key + ".host", "127.0.0.1");
        int port = this.configFile.configuration().getInt(key + ".port", 3306);
        String database = this.configFile.configuration().getString(key + ".database", "auriongo");
        String parameters = this.configFile.configuration().getString(key + ".parameters", "");
        String suffix = parameters.isBlank() ? "" : "?" + parameters;
        return "jdbc:" + driver + "://" + host + ":" + port + "/" + database + suffix;
    }
}
