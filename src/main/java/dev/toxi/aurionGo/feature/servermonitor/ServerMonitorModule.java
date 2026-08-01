package dev.toxi.aurionGo.feature.servermonitor;

import dev.toxi.aurionGo.command.ModuleDisabledCommand;
import dev.toxi.aurionGo.feature.servermonitor.command.ServerMonitorCommand;
import dev.toxi.aurionGo.message.MessageFormatter;
import dev.toxi.aurionGo.module.PluginModule;
import dev.toxi.aurionGo.shared.AurionContext;
import org.bukkit.command.PluginCommand;

public final class ServerMonitorModule implements PluginModule {
    private final AurionContext context;
    private ServerMonitorService service;

    public ServerMonitorModule(AurionContext context) {
        this.context = context;
    }

    @Override
    public String id() {
        return "servermonitor";
    }

    @Override
    public void enable() {
        this.service = new ServerMonitorService(this.context);
        this.context.serviceRegistry().register(ServerMonitorService.class, this.service);
        registerCommand("servermonitor", new ServerMonitorCommand(this.service));
        this.service.enable();
    }

    @Override
    public void disable() {
        MessageFormatter formatter = this.context.serviceRegistry().require(MessageFormatter.class);

        if (this.service != null) {
            this.service.disable();
            this.service = null;
        }

        unregisterCommand("servermonitor", formatter);
        this.context.serviceRegistry().unregister(ServerMonitorService.class);
    }

    private void registerCommand(String name, ServerMonitorCommand executor) {
        PluginCommand command = this.context.plugin().getCommand(name);

        if (command == null) {
            throw new IllegalStateException("Missing command registration in plugin.yml: " + name);
        }

        command.setExecutor(executor);
        command.setTabCompleter(null);
    }

    private void unregisterCommand(String name, MessageFormatter formatter) {
        PluginCommand command = this.context.plugin().getCommand(name);

        if (command == null) {
            return;
        }

        command.setExecutor(new ModuleDisabledCommand(formatter));
        command.setTabCompleter(null);
    }
}
