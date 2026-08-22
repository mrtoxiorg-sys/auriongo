package dev.toxi.aurionGo.feature.world;

import dev.toxi.aurionGo.command.ModuleDisabledCommand;
import dev.toxi.aurionGo.feature.world.command.WorldCommand;
import dev.toxi.aurionGo.message.MessageFormatter;
import dev.toxi.aurionGo.module.PluginModule;
import dev.toxi.aurionGo.shared.AurionContext;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;

public final class WorldModule implements PluginModule {

    private static final String COMMAND_NAME = "world";

    private final AurionContext context;
    private WorldService service;
    private WorldListener listener;

    public WorldModule(AurionContext context) {
        this.context = context;
    }

    @Override
    public String id() {
        return "world";
    }

    @Override
    public void enable() {
        this.service = new WorldService(this.context);
        this.context.serviceRegistry().register(WorldService.class, this.service);
        this.service.enable();

        this.listener = new WorldListener(this.service);
        this.context
            .plugin()
            .getServer()
            .getPluginManager()
            .registerEvents(this.listener, this.context.plugin());

        registerCommand(new WorldCommand(this.service));
    }

    @Override
    public void disable() {
        MessageFormatter formatter = this.context
            .serviceRegistry()
            .require(MessageFormatter.class);

        if (this.listener != null) {
            HandlerList.unregisterAll(this.listener);
            this.listener = null;
        }

        if (this.service != null) {
            this.service.disable();
            this.service = null;
        }

        unregisterCommand(formatter);
        this.context.serviceRegistry().unregister(WorldService.class);
    }

    private void registerCommand(WorldCommand executor) {
        PluginCommand command = this.context.plugin().getCommand(COMMAND_NAME);

        if (command == null) {
            throw new IllegalStateException(
                "Missing command registration in plugin.yml: " + COMMAND_NAME
            );
        }

        command.setExecutor(executor);
        command.setTabCompleter(null);
    }

    private void unregisterCommand(MessageFormatter formatter) {
        PluginCommand command = this.context.plugin().getCommand(COMMAND_NAME);

        if (command == null) {
            return;
        }

        command.setExecutor(new ModuleDisabledCommand(formatter));
        command.setTabCompleter(null);
    }
}
