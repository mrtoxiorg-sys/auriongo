package dev.toxi.aurionGo.command;

import dev.toxi.aurionGo.message.MessageFormatter;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

import java.util.Map;

public final class CommandMessages {
    private CommandMessages() {
    }

    public static void sendNotice(CommandSender sender, MessageFormatter formatter, String path, Map<String, String> placeholders) {
        Component prefix = formatter.renderRaw(
                formatter.getOrDefault("prefix", ""),
                Map.of()
        );
        Component body = formatter.renderRaw(
                formatter.getOrDefault(path, "<color:#FF4F4F>В конфиге отсутствует сообщение: " + path),
                placeholders
        );
        sender.sendMessage(Component.empty().append(prefix).append(body));
    }
}
