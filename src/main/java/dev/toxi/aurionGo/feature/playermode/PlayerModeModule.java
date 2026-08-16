package dev.toxi.aurionGo.feature.playermode;

import dev.toxi.aurionGo.command.ModuleDisabledCommand;
import dev.toxi.aurionGo.feature.playermode.command.FlyCommand;
import dev.toxi.aurionGo.feature.playermode.command.GodCommand;
import dev.toxi.aurionGo.message.MessageFormatter;
import dev.toxi.aurionGo.module.PluginModule;
import dev.toxi.aurionGo.shared.AurionContext;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;

public final class PlayerModeModule implements PluginModule {

    private final AurionContext context;

    public PlayerModeModule(AurionContext context) {
        this.context = context;
    }

    @Override
    public String id() {
        return "playermode";
    }

    @Override
    public void enable() {
        PlayerModeService service = new PlayerModeService(this.context);
        this.context.serviceRegistry().register(PlayerModeService.class, service);

        GodCommand godCommand = new GodCommand(service);
        FlyCommand flyCommand = new FlyCommand(service);

        registerCommand("god", godCommand, godCommand);
        registerCommand("fly", flyCommand, flyCommand);
    }

    @Override
    public void disable() {
        MessageFormatter formatter = this.context.serviceRegistry().require(MessageFormatter.class);
        unregisterCommand("god", formatter);
        unregisterCommand("fly", formatter);
        this.context.serviceRegistry().unregister(PlayerModeService.class);
    }

    private void registerCommand(String name, CommandExecutor executor, TabCompleter completer) {
        PluginCommand command = this.context.plugin().getCommand(name);

        if (command == null) {
            throw new IllegalStateException("Missing command registration in plugin.yml: " + name);
        }

        command.setExecutor(executor);
        command.setTabCompleter(completer);
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