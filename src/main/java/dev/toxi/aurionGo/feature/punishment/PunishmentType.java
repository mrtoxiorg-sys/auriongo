package dev.toxi.aurionGo.feature.punishment;

public enum PunishmentType {
    BAN("ban"),
    KICK("kick"),
    MUTE("mute"),
    WARN("warn");

    private final String key;

    PunishmentType(String key) {
        this.key = key;
    }

    public String key() {
        return this.key;
    }
}
