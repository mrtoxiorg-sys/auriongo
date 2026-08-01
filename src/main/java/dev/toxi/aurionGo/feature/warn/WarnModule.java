package dev.toxi.aurionGo.feature.warn;

import dev.toxi.aurionGo.module.PluginModule;
import dev.toxi.aurionGo.shared.AurionContext;

public final class WarnModule implements PluginModule {
    private final AurionContext context;

    public WarnModule(AurionContext context) {
        this.context = context;
    }

    @Override
    public String id() {
        return "warns";
    }

    @Override
    public void enable() {
        // Warn storage and escalation rules will be configured here.
    }

    @Override
    public void disable() {
        // Reserved for warn module shutdown.
    }
}
