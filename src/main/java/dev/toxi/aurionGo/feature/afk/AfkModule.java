package dev.toxi.aurionGo.feature.afk;

import dev.toxi.aurionGo.command.ModuleDisabledCommand;
import dev.toxi.aurionGo.config.StandardConfigs;
import dev.toxi.aurionGo.feature.afk.command.AfkCommand;
import dev.toxi.aurionGo.message.MessageFormatter;
import dev.toxi.aurionGo.module.PluginModule;
import dev.toxi.aurionGo.shared.AurionContext;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;

public final class AfkModule implements PluginModule {

    private final AurionContext context;
    private AfkService service;
    private AfkListener listener;
    private AfkPlaceholderExpansion placeholderExpansion;

    public AfkModule(AurionContext context) {
        this.context = context;
    }

    @Override
    public String id() {
        return "afk";
    }

    @Override
    public void enable() {
        this.service = new AfkService(this.context);
        this.context.serviceRegistry().register(AfkService.class, this.service);
        this.listener = new AfkListener(this.service);
        this.context
            .plugin()
            .getServer()
            .getPluginManager()
            .registerEvents(this.listener, this.context.plugin());
        registerCommand("afk", new AfkCommand(this.service));
        this.service.start();
        registerPlaceholderExpansion();
    }

    private void registerPlaceholderExpansion() {
        if (
            this.context
                .plugin()
                .getServer()
                .getPluginManager()
                .getPlugin("PlaceholderAPI") ==
            null
        ) {
            return;
        }

        this.placeholderExpansion = new AfkPlaceholderExpansion(
            this.service,
            this.context.configManager().require(StandardConfigs.AFK),
            this.context.plugin().getDescription().getVersion()
        );
        this.placeholderExpansion.register();
    }

    @Override
    public void disable() {
        MessageFormatter formatter = this.context
            .serviceRegistry()
            .require(MessageFormatter.class);

        if (this.placeholderExpansion != null) {
            this.placeholderExpansion.unregister();
            this.placeholderExpansion = null;
        }

        if (this.listener != null) {
            HandlerList.unregisterAll(this.listener);
            this.listener = null;
        }

        if (this.service != null) {
            this.service.shutdown();
            this.service = null;
        }

        unregisterCommand("afk", formatter);
        this.context.serviceRegistry().unregister(AfkService.class);
    }

    private void registerCommand(String name, AfkCommand executor) {
        PluginCommand command = this.context.plugin().getCommand(name);

        if (command == null) {
            throw new IllegalStateException(
                "Missing command registration in plugin.yml: " + name
            );
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
