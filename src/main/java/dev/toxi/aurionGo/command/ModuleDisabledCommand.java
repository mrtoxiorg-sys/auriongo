package dev.toxi.aurionGo.command;

import dev.toxi.aurionGo.message.MessageFormatter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Map;

public final class ModuleDisabledCommand implements CommandExecutor {
    private final MessageFormatter formatter;

    public ModuleDisabledCommand(MessageFormatter formatter) {
        this.formatter = formatter;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        CommandMessages.sendNotice(sender, this.formatter, "general.module-disabled", Map.of());
        return true;
    }
}
