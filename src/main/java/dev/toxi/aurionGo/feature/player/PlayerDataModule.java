package dev.toxi.aurionGo.feature.player;

import dev.toxi.aurionGo.feature.player.command.CheckPlayerCommand;
import dev.toxi.aurionGo.module.PluginModule;
import dev.toxi.aurionGo.shared.AurionContext;
import dev.toxi.aurionGo.storage.player.PlayerProfileRepository;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public final class PlayerDataModule implements PluginModule {
    private final AurionContext context;
    private PlayerProfileService profileService;
    private PlayerDataListener listener;

    public PlayerDataModule(AurionContext context) {
        this.context = context;
    }

    @Override
    public String id() {
        return "player-data";
    }

    @Override
    public void enable() {
        PlayerProfileRepository repository = this.context.serviceRegistry().require(PlayerProfileRepository.class);
        this.profileService = new PlayerProfileService(this.context, repository);
        this.context.serviceRegistry().register(PlayerProfileService.class, this.profileService);
        this.listener = new PlayerDataListener(this.profileService);
        CheckPlayerCommand checkPlayerCommand = new CheckPlayerCommand(this.profileService);
        this.context.plugin().getServer().getPluginManager().registerEvents(this.listener, this.context.plugin());
        registerCommand("checkplayer", checkPlayerCommand, checkPlayerCommand);

        for (Player player : this.context.plugin().getServer().getOnlinePlayers()) {
            this.profileService.trackJoin(player);
        }
    }

    @Override
    public void disable() {
        if (this.listener != null) {
            HandlerList.unregisterAll(this.listener);
            this.listener = null;
        }

        this.profileService = null;
    }

    private void registerCommand(String name, CommandExecutor executor, TabCompleter tabCompleter) {
        PluginCommand command = this.context.plugin().getCommand(name);

        if (command == null) {
            throw new IllegalStateException("Missing command registration in plugin.yml: " + name);
        }

        command.setExecutor(executor);

        if (tabCompleter != null) {
            command.setTabCompleter(tabCompleter);
        }
    }
}
