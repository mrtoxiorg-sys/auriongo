package dev.toxi.aurionGo.feature.integration;

import dev.toxi.aurionGo.config.StandardConfigs;
import dev.toxi.aurionGo.feature.punishment.PunishmentService;
import dev.toxi.aurionGo.module.PluginModule;
import dev.toxi.aurionGo.shared.AurionContext;
import org.bukkit.configuration.file.FileConfiguration;

public final class IntegrationModule implements PluginModule {
    private final AurionContext context;
    private SimpleVoiceChatBridge simpleVoiceChatBridge;

    public IntegrationModule(AurionContext context) {
        this.context = context;
    }

    @Override
    public String id() {
        return "integrations";
    }

    @Override
    public void enable() {
        FileConfiguration configuration = this.context.configManager().require(StandardConfigs.INTEGRATIONS).configuration();

        if (!configuration.getBoolean("simple-voice-chat.hook", true)
                || !configuration.getBoolean("simple-voice-chat.mirror-mutes", true)
                || !this.context.configManager().require(StandardConfigs.PUNISHMENTS).configuration().getBoolean("mutes.sync-with-voice-chat", true)) {
            return;
        }

        PunishmentService punishmentService;

        try {
            punishmentService = this.context.serviceRegistry().require(PunishmentService.class);
        } catch (IllegalStateException exception) {
            if (!configuration.getBoolean("simple-voice-chat.fail-silently", true)) {
                this.context.plugin().getLogger().warning("Интеграция с Simple Voice Chat пропущена: сервис мутов недоступен.");
            }
            return;
        }

        this.simpleVoiceChatBridge = new SimpleVoiceChatBridge(this.context, punishmentService);
        this.simpleVoiceChatBridge.enable();
    }

    @Override
    public void disable() {
        if (this.simpleVoiceChatBridge != null) {
            this.simpleVoiceChatBridge.disable();
            this.simpleVoiceChatBridge = null;
        }
    }
}
