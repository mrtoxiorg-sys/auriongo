package dev.toxi.aurionGo.feature.punishment;

import dev.toxi.aurionGo.feature.punishment.command.PunishmentCommand;
import dev.toxi.aurionGo.feature.punishment.command.PunishmentListCommand;
import dev.toxi.aurionGo.feature.punishment.command.PunishmentRemoveCommand;
import dev.toxi.aurionGo.module.PluginModule;
import dev.toxi.aurionGo.shared.AurionContext;
import dev.toxi.aurionGo.storage.player.PlayerProfileRepository;
import dev.toxi.aurionGo.storage.punishment.PunishmentRepository;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.HandlerList;

public final class PunishmentModule implements PluginModule {
    private final AurionContext context;
    private PunishmentListener listener;

    public PunishmentModule(AurionContext context) {
        this.context = context;
    }

    @Override
    public String id() {
        return "punishments";
    }

    @Override
    public void enable() {
        PunishmentRepository punishmentRepository = new PunishmentRepository(this.context.serviceRegistry().require(dev.toxi.aurionGo.storage.DatabaseManager.class));
        PlayerProfileRepository playerProfileRepository = this.context.serviceRegistry().require(PlayerProfileRepository.class);
        PunishmentService service = new PunishmentService(this.context, punishmentRepository, playerProfileRepository);
        this.context.serviceRegistry().register(PunishmentService.class, service);
        this.listener = new PunishmentListener(service);
        this.context.plugin().getServer().getPluginManager().registerEvents(this.listener, this.context.plugin());

        PunishmentCommand banCommand = new PunishmentCommand(service, PunishmentType.BAN);
        PunishmentCommand kickCommand = new PunishmentCommand(service, PunishmentType.KICK);
        PunishmentCommand muteCommand = new PunishmentCommand(service, PunishmentType.MUTE);
        PunishmentCommand warnCommand = new PunishmentCommand(service, PunishmentType.WARN);

        registerCommand("ban", banCommand, banCommand);
        registerCommand("kick", kickCommand, kickCommand);
        registerCommand("mute", muteCommand, muteCommand);
        registerCommand("warn", warnCommand, warnCommand);
        PunishmentListCommand banListCommand = new PunishmentListCommand(service, PunishmentType.BAN);
        PunishmentListCommand muteListCommand = new PunishmentListCommand(service, PunishmentType.MUTE);
        PunishmentListCommand warnListCommand = new PunishmentListCommand(service, PunishmentType.WARN);
        PunishmentRemoveCommand unbanCommand = new PunishmentRemoveCommand(service, PunishmentType.BAN);
        PunishmentRemoveCommand unmuteCommand = new PunishmentRemoveCommand(service, PunishmentType.MUTE);
        PunishmentRemoveCommand unwarnCommand = new PunishmentRemoveCommand(service, PunishmentType.WARN);

        registerCommand("banlist", banListCommand, banListCommand);
        registerCommand("mutelist", muteListCommand, muteListCommand);
        registerCommand("warnlist", warnListCommand, warnListCommand);
        registerCommand("unban", unbanCommand, unbanCommand);
        registerCommand("unmute", unmuteCommand, unmuteCommand);
        registerCommand("unwarn", unwarnCommand, unwarnCommand);
    }

    @Override
    public void disable() {
        if (this.listener != null) {
            HandlerList.unregisterAll(this.listener);
            this.listener = null;
        }
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
