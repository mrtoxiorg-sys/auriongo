package dev.toxi.aurionGo.storage;

public enum DatabaseType {
    SQLITE("sqlite", "org.sqlite.JDBC"),
    MYSQL("mysql", "com.mysql.cj.jdbc.Driver"),
    MARIADB("mariadb", "org.mariadb.jdbc.Driver");

    private final String configKey;
    private final String driverClassName;

    DatabaseType(String configKey, String driverClassName) {
        this.configKey = configKey;
        this.driverClassName = driverClassName;
    }

    public String configKey() {
        return this.configKey;
    }

    public String driverClassName() {
        return this.driverClassName;
    }

    public static DatabaseType fromConfig(String raw) {
        for (DatabaseType value : values()) {
            if (value.name().equalsIgnoreCase(raw) || value.configKey.equalsIgnoreCase(raw)) {
                return value;
            }
        }

        throw new IllegalArgumentException("Неподдерживаемый тип хранилища: " + raw);
    }
}
