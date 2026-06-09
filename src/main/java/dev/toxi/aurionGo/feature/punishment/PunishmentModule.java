package dev.toxi.aurionGo.feature.punishment;

import dev.toxi.aurionGo.module.PluginModule;
import dev.toxi.aurionGo.shared.AurionContext;

public final class PunishmentModule implements PluginModule {
    private final AurionContext context;

    public PunishmentModule(AurionContext context) {
        this.context = context;
    }

    @Override
    public String id() {
        return "punishments";
    }

    @Override
    public void enable() {
        // Ban, mute and kick services will be wired here.
    }

    @Override
    public void disable() {
        // Reserved for punishment state cleanup.
    }
}
