package dev.toxi.aurionGo.command;

import dev.toxi.aurionGo.AurionGo;
import dev.toxi.aurionGo.bootstrap.AurionBootstrap;
import dev.toxi.aurionGo.message.MessageFormatter;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public final class AurionGoCommand implements CommandExecutor, TabCompleter {

    private final AurionGo plugin;
    private final AurionBootstrap bootstrap;
    private final MessageFormatter formatter;

    public AurionGoCommand(
        AurionGo plugin,
        AurionBootstrap bootstrap,
        MessageFormatter formatter
    ) {
        this.plugin = plugin;
        this.bootstrap = bootstrap;
        this.formatter = formatter;
    }

    @Override
    public boolean onCommand(
        CommandSender sender,
        Command command,
        String label,
        String[] args
    ) {
        if (!sender.hasPermission("auriongo.command.misc.reload")) {
            CommandMessages.sendNotice(
                sender,
                this.formatter,
                "errors.no-permission",
                Map.of()
            );
            return true;
        }

        if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) {
            CommandMessages.sendNotice(
                sender,
                this.formatter,
                "general.reload-usage",
                Map.of("command", label.toLowerCase())
            );
            return true;
        }

        try {
            this.bootstrap.reload();
            CommandMessages.sendNotice(
                sender,
                this.formatter,
                "general.reload-success",
                Map.of()
            );
        } catch (Exception exception) {
            this.plugin
                .getLogger()
                .log(
                    Level.SEVERE,
                    "Не удалось перезагрузить конфигурацию AurionGo.",
                    exception
                );
            CommandMessages.sendNotice(
                sender,
                this.formatter,
                "general.reload-failure",
                Map.of("error", resolveErrorMessage(exception))
            );
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(
        CommandSender sender,
        Command command,
        String alias,
        String[] args
    ) {
        if (!sender.hasPermission("auriongo.command.misc.reload")) {
            return List.of();
        }

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return List.of("reload")
                .stream()
                .filter(option -> option.startsWith(partial))
                .toList();
        }

        return List.of();
    }

    private String resolveErrorMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
            ? exception.getClass().getSimpleName()
            : message;
    }
}
