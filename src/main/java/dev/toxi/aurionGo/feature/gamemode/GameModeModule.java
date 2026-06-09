package dev.toxi.aurionGo.feature.gamemode;

import dev.toxi.aurionGo.feature.gamemode.command.GameModeCommand;
import dev.toxi.aurionGo.module.PluginModule;
import dev.toxi.aurionGo.shared.AurionContext;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;

public final class GameModeModule implements PluginModule {
    private final AurionContext context;

    public GameModeModule(AurionContext context) {
        this.context = context;
    }

    @Override
    public String id() {
        return "gamemode";
    }

    @Override
    public void enable() {
        GameModeService service = new GameModeService(this.context);
        this.context.serviceRegistry().register(GameModeService.class, service);
        GameModeCommand gmCommand = new GameModeCommand(service, null);
        GameModeCommand creativeCommand = new GameModeCommand(service, org.bukkit.GameMode.CREATIVE);
        GameModeCommand survivalCommand = new GameModeCommand(service, org.bukkit.GameMode.SURVIVAL);
        GameModeCommand spectatorCommand = new GameModeCommand(service, org.bukkit.GameMode.SPECTATOR);
        GameModeCommand adventureCommand = new GameModeCommand(service, org.bukkit.GameMode.ADVENTURE);

        registerCommand("gamemode", gmCommand, gmCommand);
        registerCommand("gmc", creativeCommand, creativeCommand);
        registerCommand("gms", survivalCommand, survivalCommand);
        registerCommand("gmsp", spectatorCommand, spectatorCommand);
        registerCommand("gma", adventureCommand, adventureCommand);
    }

    @Override
    public void disable() {
    }

    private void registerCommand(String name, CommandExecutor executor, TabCompleter completer) {
        PluginCommand command = this.context.plugin().getCommand(name);

        if (command == null) {
            throw new IllegalStateException("Missing command registration in plugin.yml: " + name);
        }

        command.setExecutor(executor);
        command.setTabCompleter(completer);
    }
}
