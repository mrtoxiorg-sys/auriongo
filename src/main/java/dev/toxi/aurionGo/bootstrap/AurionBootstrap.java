package dev.toxi.aurionGo.bootstrap;

import dev.toxi.aurionGo.AurionGo;
import dev.toxi.aurionGo.config.ConfigManager;
import dev.toxi.aurionGo.config.StandardConfigs;
import dev.toxi.aurionGo.feature.chat.ChatModule;
import dev.toxi.aurionGo.feature.integration.IntegrationModule;
import dev.toxi.aurionGo.feature.punishment.PunishmentModule;
import dev.toxi.aurionGo.feature.warn.WarnModule;
import dev.toxi.aurionGo.message.ConfigBackedMessageService;
import dev.toxi.aurionGo.message.MessageService;
import dev.toxi.aurionGo.module.ModuleManager;
import dev.toxi.aurionGo.shared.AurionContext;
import dev.toxi.aurionGo.shared.ServiceRegistry;

public final class AurionBootstrap {
    private final AurionGo plugin;
    private ModuleManager moduleManager;

    public AurionBootstrap(AurionGo plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        ConfigManager configManager = new ConfigManager(this.plugin);
        configManager.register(StandardConfigs.CORE);
        configManager.register(StandardConfigs.MESSAGES);
        configManager.register(StandardConfigs.CHAT);
        configManager.register(StandardConfigs.PUNISHMENTS);
        configManager.register(StandardConfigs.WARNS);
        configManager.register(StandardConfigs.INTEGRATIONS);

        ServiceRegistry serviceRegistry = new ServiceRegistry();
        serviceRegistry.register(ConfigManager.class, configManager);
        serviceRegistry.register(
                MessageService.class,
                new ConfigBackedMessageService(configManager.require(StandardConfigs.MESSAGES))
        );

        AurionContext context = new AurionContext(this.plugin, configManager, serviceRegistry);
        this.moduleManager = new ModuleManager(context);
        this.moduleManager.register(new ChatModule(context));
        this.moduleManager.register(new PunishmentModule(context));
        this.moduleManager.register(new WarnModule(context));
        this.moduleManager.register(new IntegrationModule(context));
        this.moduleManager.enableAll();
    }

    public void disable() {
        if (this.moduleManager != null) {
            this.moduleManager.disableAll();
            this.moduleManager = null;
        }
    }
}
