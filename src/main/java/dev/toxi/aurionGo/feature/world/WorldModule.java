package dev.toxi.aurionGo.feature.world;

import dev.toxi.aurionGo.command.ModuleDisabledCommand;
import dev.toxi.aurionGo.message.MessageFormatter;
import dev.toxi.aurionGo.module.PluginModule;
import dev.toxi.aurionGo.shared.AurionContext;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;

public final class WorldModule implements PluginModule {

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
        this.listener = new WorldListener(this.service);

        this.service.enable();
        this.context
            .plugin()
            .getServer()
            .getPluginManager()
            .registerEvents(this.listener, this.context.plugin());

        PluginCommand command = this.context.plugin().getCommand("world");

        if (command == null) {
            throw new IllegalStateException(
                "Missing command registration in plugin.yml: world"
            );
        }

        command.setExecutor(new WorldCommand(this.service));
        command.setTabCompleter(null);
    }

    @Override
    public void disable() {
        MessageFormatter formatter = this.context
            .serviceRegistry()
            .require(MessageFormatter.class);
        PluginCommand command = this.context.plugin().getCommand("world");

        if (this.listener != null) {
            HandlerList.unregisterAll(this.listener);
            this.listener = null;
        }

        if (this.service != null) {
            this.service.disable();
            this.service = null;
        }

        if (command != null) {
            command.setExecutor(new ModuleDisabledCommand(formatter));
            command.setTabCompleter(null);
        }
    }
}
