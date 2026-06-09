package dev.toxi.aurionGo.feature.integration;

import dev.toxi.aurionGo.module.PluginModule;
import dev.toxi.aurionGo.shared.AurionContext;

public final class IntegrationModule implements PluginModule {
    private final AurionContext context;

    public IntegrationModule(AurionContext context) {
        this.context = context;
    }

    @Override
    public String id() {
        return "integrations";
    }

    @Override
    public void enable() {
        // PlaceholderAPI and Simple Voice Chat bridges will be initialized here.
    }

    @Override
    public void disable() {
        // Reserved for future integration shutdown.
    }
}
