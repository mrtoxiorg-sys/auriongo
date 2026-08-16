package dev.toxi.aurionGo.bootstrap;

import dev.toxi.aurionGo.AurionGo;
import dev.toxi.aurionGo.command.AurionGoCommand;
import dev.toxi.aurionGo.config.ConfigManager;
import dev.toxi.aurionGo.config.StandardConfigs;
import dev.toxi.aurionGo.feature.afk.AfkModule;
import dev.toxi.aurionGo.feature.chat.ChatModule;
import dev.toxi.aurionGo.feature.gamemode.GameModeModule;
import dev.toxi.aurionGo.feature.integration.IntegrationModule;
import dev.toxi.aurionGo.feature.player.PlayerDataModule;
import dev.toxi.aurionGo.feature.playermode.PlayerModeModule;
import dev.toxi.aurionGo.feature.punishment.PunishmentModule;
import dev.toxi.aurionGo.feature.servermonitor.ServerMonitorModule;
import dev.toxi.aurionGo.feature.warn.WarnModule;
import dev.toxi.aurionGo.feature.world.WorldModule;
import dev.toxi.aurionGo.message.ConfigBackedMessageService;
import dev.toxi.aurionGo.message.MessageFormatter;
import dev.toxi.aurionGo.message.MessageService;
import dev.toxi.aurionGo.module.ModuleManager;
import dev.toxi.aurionGo.shared.AurionContext;
import dev.toxi.aurionGo.shared.ServiceRegistry;
import dev.toxi.aurionGo.storage.DatabaseManager;
import dev.toxi.aurionGo.storage.player.PlayerProfileRepository;
import org.bukkit.command.PluginCommand;

public final class AurionBootstrap {

    private final AurionGo plugin;
    private ConfigManager configManager;
    private ServiceRegistry serviceRegistry;
    private ModuleManager moduleManager;
    private DatabaseManager databaseManager;

    public AurionBootstrap(AurionGo plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        this.configManager = new ConfigManager(this.plugin);
        this.configManager.register(StandardConfigs.CORE);
        this.configManager.register(StandardConfigs.MESSAGES);
        this.configManager.register(StandardConfigs.CHAT);
        this.configManager.register(StandardConfigs.AFK);
        this.configManager.register(StandardConfigs.GAMEMODES);
        this.configManager.register(StandardConfigs.PUNISHMENTS);
        this.configManager.register(StandardConfigs.WARNS);
        this.configManager.register(StandardConfigs.INTEGRATIONS);
        this.configManager.register(StandardConfigs.SERVER_MONITOR);
        this.configManager.register(StandardConfigs.WORLD);

        this.serviceRegistry = new ServiceRegistry();
        this.serviceRegistry.register(ConfigManager.class, this.configManager);
        this.serviceRegistry.register(
            MessageService.class,
            new ConfigBackedMessageService(
                this.configManager.require(StandardConfigs.MESSAGES)
            )
        );
        this.serviceRegistry.register(
            MessageFormatter.class,
            new MessageFormatter(
                this.configManager.require(StandardConfigs.CORE),
                this.serviceRegistry.require(MessageService.class)
            )
        );
        this.databaseManager = new DatabaseManager(
            this.configManager.require(StandardConfigs.CORE)
        );
        this.databaseManager.initialize();
        this.serviceRegistry.register(
            DatabaseManager.class,
            this.databaseManager
        );
        this.serviceRegistry.register(
            PlayerProfileRepository.class,
            new PlayerProfileRepository(this.databaseManager)
        );

        AurionContext context = new AurionContext(
            this.plugin,
            this.configManager,
            this.serviceRegistry
        );
        this.moduleManager = new ModuleManager(context);
        this.moduleManager.register(new PlayerDataModule(context));
        this.moduleManager.register(new ChatModule(context));
        this.moduleManager.register(new AfkModule(context));
        this.moduleManager.register(new GameModeModule(context));
        this.moduleManager.register(new PlayerModeModule(context));
        this.moduleManager.register(new PunishmentModule(context));
        this.moduleManager.register(new WarnModule(context));
        this.moduleManager.register(new IntegrationModule(context));
        this.moduleManager.register(new ServerMonitorModule(context));
        this.moduleManager.register(new WorldModule(context));
        registerRootCommand();
        this.moduleManager.enableAll();
    }

    public void reload() {
        ensureEnabled();
        this.moduleManager.disableAll();

        try {
            this.configManager.reloadAll();
            this.databaseManager.initialize();
            this.moduleManager.enableAll();
        } catch (Exception exception) {
            try {
                this.moduleManager.disableAll();
            } catch (Exception suppressed) {
                exception.addSuppressed(suppressed);
            }

            throw exception;
        }
    }

    public void disable() {
        if (this.moduleManager != null) {
            this.moduleManager.disableAll();
            this.moduleManager = null;
        }

        if (this.databaseManager != null) {
            this.databaseManager.close();
            this.databaseManager = null;
        }

        this.serviceRegistry = null;
        this.configManager = null;
    }

    private void registerRootCommand() {
        PluginCommand command = this.plugin.getCommand("auriongo");

        if (command == null) {
            throw new IllegalStateException(
                "Missing command registration in plugin.yml: auriongo"
            );
        }

        AurionGoCommand executor = new AurionGoCommand(
            this.plugin,
            this,
            this.serviceRegistry.require(MessageFormatter.class)
        );
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void ensureEnabled() {
        if (
            this.moduleManager == null ||
            this.configManager == null ||
            this.databaseManager == null
        ) {
            throw new IllegalStateException("AurionGo еще не инициализирован.");
        }
    }
}
