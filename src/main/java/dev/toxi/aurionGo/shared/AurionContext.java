package dev.toxi.aurionGo.shared;

import dev.toxi.aurionGo.AurionGo;
import dev.toxi.aurionGo.config.ConfigManager;

public record AurionContext(
        AurionGo plugin,
        ConfigManager configManager,
        ServiceRegistry serviceRegistry
) {
}
