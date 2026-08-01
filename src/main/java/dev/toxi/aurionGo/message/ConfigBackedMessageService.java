package dev.toxi.aurionGo.message;

import dev.toxi.aurionGo.config.ConfigFile;

public final class ConfigBackedMessageService implements MessageService {
    private final ConfigFile messagesConfig;

    public ConfigBackedMessageService(ConfigFile messagesConfig) {
        this.messagesConfig = messagesConfig;
    }

    @Override
    public String get(String path) {
        return this.messagesConfig.configuration().getString(path);
    }

    @Override
    public String getOrDefault(String path, String fallback) {
        return this.messagesConfig.configuration().getString(path, fallback);
    }
}
