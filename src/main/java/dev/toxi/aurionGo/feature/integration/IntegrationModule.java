package dev.toxi.aurionGo.feature.integration;

import dev.toxi.aurionGo.config.StandardConfigs;
import dev.toxi.aurionGo.feature.player.PlayerProfileService;
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
        FileConfiguration configuration = this.context
            .configManager()
            .require(StandardConfigs.INTEGRATIONS)
            .configuration();

        enableSimpleVoiceChat(configuration);
    }

    private void enableSimpleVoiceChat(FileConfiguration configuration) {
        if (!configuration.getBoolean("simple-voice-chat.hook", true)) {
            return;
        }

        boolean mirrorMutes = configuration.getBoolean("simple-voice-chat.mirror-mutes", true) &&
            this.context
                .configManager()
                .require(StandardConfigs.PUNISHMENTS)
                .configuration()
                .getBoolean("mutes.sync-with-voice-chat", true);
        boolean speakingIndicator = configuration.getBoolean(
            "simple-voice-chat.hidden-nametag-speaking-indicator",
            true
        );

        PunishmentService punishmentService = mirrorMutes
            ? resolvePunishmentService(configuration)
            : null;
        PlayerProfileService profileService = speakingIndicator
            ? resolvePlayerProfileService(configuration)
            : null;

        if (punishmentService == null && profileService == null) {
            return;
        }

        this.simpleVoiceChatBridge = new SimpleVoiceChatBridge(
            this.context,
            punishmentService,
            profileService
        );
        this.simpleVoiceChatBridge.enable();
    }

    private PunishmentService resolvePunishmentService(
        FileConfiguration configuration
    ) {
        try {
            return this.context
                .serviceRegistry()
                .require(PunishmentService.class);
        } catch (IllegalStateException exception) {
            warnUnavailable(
                configuration,
                "Интеграция мутов с Simple Voice Chat пропущена: сервис мутов недоступен."
            );
            return null;
        }
    }

    private PlayerProfileService resolvePlayerProfileService(
        FileConfiguration configuration
    ) {
        try {
            return this.context
                .serviceRegistry()
                .require(PlayerProfileService.class);
        } catch (IllegalStateException exception) {
            warnUnavailable(
                configuration,
                "Индикатор Simple Voice Chat для скрытых ников пропущен: сервис профилей игроков недоступен."
            );
            return null;
        }
    }

    private void warnUnavailable(
        FileConfiguration configuration,
        String message
    ) {
        if (!configuration.getBoolean("simple-voice-chat.fail-silently", true)) {
            this.context.plugin().getLogger().warning(message);
        }
    }

    @Override
    public void disable() {
        if (this.simpleVoiceChatBridge != null) {
            this.simpleVoiceChatBridge.disable();
            this.simpleVoiceChatBridge = null;
        }
    }
}
