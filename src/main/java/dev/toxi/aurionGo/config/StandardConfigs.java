package dev.toxi.aurionGo.config;

public final class StandardConfigs {
    public static final ConfigDefinition CORE = new ConfigDefinition("core", "config.yml");
    public static final ConfigDefinition MESSAGES = new ConfigDefinition("messages", "messages.yml");
    public static final ConfigDefinition CHAT = new ConfigDefinition("chat", "chat.yml");
    public static final ConfigDefinition AFK = new ConfigDefinition("afk", "afk.yml");
    public static final ConfigDefinition GAMEMODES = new ConfigDefinition("gamemodes", "gamemodes.yml");
    public static final ConfigDefinition PUNISHMENTS = new ConfigDefinition("punishments", "punishments.yml");
    public static final ConfigDefinition WARNS = new ConfigDefinition("warns", "warns.yml");
    public static final ConfigDefinition INTEGRATIONS = new ConfigDefinition("integrations", "integrations.yml");
    public static final ConfigDefinition SERVER_MONITOR = new ConfigDefinition("server-monitor", "servermonitor.yml");
    public static final ConfigDefinition WORLD = new ConfigDefinition("world", "world.yml");

    private StandardConfigs() {
    }
}
