package dev.toxi.aurionGo.feature.chat;

import dev.toxi.aurionGo.command.ModuleDisabledCommand;
import dev.toxi.aurionGo.feature.chat.command.BroadcastCommand;
import dev.toxi.aurionGo.feature.chat.command.MessageCommand;
import dev.toxi.aurionGo.feature.chat.command.ReplyCommand;
import dev.toxi.aurionGo.feature.chat.command.SpyCommand;
import dev.toxi.aurionGo.message.MessageFormatter;
import dev.toxi.aurionGo.module.PluginModule;
import dev.toxi.aurionGo.shared.AurionContext;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.HandlerList;

public final class ChatModule implements PluginModule {

    private final AurionContext context;
    private ChatService chatService;
    private ChatListener chatListener;

    public ChatModule(AurionContext context) {
        this.context = context;
    }

    @Override
    public String id() {
        return "chat";
    }

    @Override
    public void enable() {
        this.chatService = new ChatService(this.context);
        this.context.serviceRegistry().register(ChatService.class, this.chatService);
        this.chatListener = new ChatListener(this.chatService);
        MessageCommand messageCommand = new MessageCommand(this.chatService);

        this.context
            .plugin()
            .getServer()
            .getPluginManager()
            .registerEvents(this.chatListener, this.context.plugin());
        registerCommand("msg", messageCommand, messageCommand);
        registerCommand("r", new ReplyCommand(this.chatService), null);
        registerCommand("broadcast", new BroadcastCommand(this.chatService), null);
        registerCommand("spy", new SpyCommand(this.chatService), null);
    }

    @Override
    public void disable() {
        MessageFormatter formatter = this.context
            .serviceRegistry()
            .require(MessageFormatter.class);

        if (this.chatListener != null) {
            HandlerList.unregisterAll(this.chatListener);
            this.chatListener = null;
        }

        if (this.chatService != null) {
            this.context.serviceRegistry().unregister(ChatService.class);
            this.chatService.shutdown();
            this.chatService = null;
        }

        unregisterCommand("msg", formatter);
        unregisterCommand("r", formatter);
        unregisterCommand("broadcast", formatter);
        unregisterCommand("spy", formatter);
    }

    private void registerCommand(
        String name,
        CommandExecutor executor,
        TabCompleter tabCompleter
    ) {
        PluginCommand command = this.context.plugin().getCommand(name);

        if (command == null) {
            throw new IllegalStateException(
                "Missing command registration in plugin.yml: " + name
            );
        }

        command.setExecutor(executor);

        if (tabCompleter != null) {
            command.setTabCompleter(tabCompleter);
        }
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
