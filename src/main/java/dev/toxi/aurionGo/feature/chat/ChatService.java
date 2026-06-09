package dev.toxi.aurionGo.feature.chat;

import dev.toxi.aurionGo.config.ConfigFile;
import dev.toxi.aurionGo.config.StandardConfigs;
import dev.toxi.aurionGo.message.MessageService;
import dev.toxi.aurionGo.shared.AurionContext;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ChatService {
    private final AurionContext context;
    private final ConfigFile chatConfig;
    private final ConfigFile messagesConfig;
    private final MessageService messageService;
    private final PlaceholderApiBridge placeholderApiBridge;
    private final ReplyTracker replyTracker = new ReplyTracker();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final PlainTextComponentSerializer plainTextSerializer = PlainTextComponentSerializer.plainText();
    private final LegacyComponentSerializer legacySectionSerializer = LegacyComponentSerializer.legacySection();

    public ChatService(AurionContext context) {
        this.context = context;
        this.chatConfig = context.configManager().require(StandardConfigs.CHAT);
        this.messagesConfig = context.configManager().require(StandardConfigs.MESSAGES);
        this.messageService = context.serviceRegistry().require(MessageService.class);
        this.placeholderApiBridge = new PlaceholderApiBridge(context.plugin());
    }

    public void configurePublicChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        String rawMessage = this.plainTextSerializer.serialize(event.message());
        Component renderedMessage = parsePlayerInput(sender, rawMessage, true);
        event.message(renderedMessage);
        event.renderer((Player source, Component sourceDisplayName, Component message, Audience viewer) ->
                createChatLine(
                        source,
                        "formats.global",
                        Map.of("player", source.getName(), "world", source.getWorld().getName()),
                        Map.of("message", message)
                )
        );
    }

    public Component createJoinMessage(Player player) {
        if (!this.chatConfig.configuration().getBoolean("join-message.enabled", true)) {
            return null;
        }

        return createChatLine(
                player,
                "join-message.format",
                Map.of("player", player.getName(), "world", player.getWorld().getName()),
                Map.of()
        );
    }

    public Component createQuitMessage(Player player) {
        if (!this.chatConfig.configuration().getBoolean("quit-message.enabled", true)) {
            return null;
        }

        return createChatLine(
                player,
                "quit-message.format",
                Map.of("player", player.getName(), "world", player.getWorld().getName()),
                Map.of()
        );
    }

    public void sendDo(Player sender, String rawInput) {
        Component message = parsePlayerInput(sender, rawInput, false);
        Component rendered = createChatLine(
                sender,
                "formats.do",
                Map.of("player", sender.getName(), "world", sender.getWorld().getName()),
                Map.of("message", message)
        );

        double radius = this.chatConfig.configuration().getDouble("commands.do.radius", 100.0D);
        double radiusSquared = radius * radius;

        for (Player target : sender.getWorld().getPlayers()) {
            if (target.getLocation().distanceSquared(sender.getLocation()) <= radiusSquared) {
                target.sendMessage(rendered);
            }
        }

        this.context.plugin().getServer().getConsoleSender().sendMessage(rendered);
    }

    public boolean sendPrivateMessage(Player sender, Player target, String rawInput) {
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            sendChatNotice(sender, "messages.cannot-message-self", Map.of(), Map.of());
            return false;
        }

        Component message = parsePlayerInput(sender, rawInput, false);
        Component sent = createChatLine(
                sender,
                "formats.private-message-sent",
                Map.of("player", sender.getName(), "target", target.getName()),
                Map.of("message", message)
        );
        Component received = createChatLine(
                target,
                "formats.private-message-received",
                Map.of("player", sender.getName(), "target", target.getName()),
                Map.of("message", message)
        );

        sender.sendMessage(sent);
        target.sendMessage(received);
        this.replyTracker.link(sender.getUniqueId(), target.getUniqueId());
        return true;
    }

    public boolean sendReply(Player sender, String rawInput) {
        UUID replyTargetId = this.replyTracker.getReplyTarget(sender.getUniqueId());

        if (replyTargetId == null) {
            sendChatNotice(sender, "messages.reply-target-missing", Map.of(), Map.of());
            return false;
        }

        Player target = Bukkit.getPlayer(replyTargetId);

        if (target == null) {
            this.replyTracker.clear(sender.getUniqueId());
            sendChatNotice(sender, "messages.reply-target-offline", Map.of(), Map.of());
            return false;
        }

        return sendPrivateMessage(sender, target, rawInput);
    }

    public Player findOnlinePlayer(String input) {
        return Bukkit.getPlayer(input);
    }

    public void sendChatNotice(CommandSender sender, String path, Map<String, String> stringPlaceholders, Map<String, Component> componentPlaceholders) {
        Component prefix = parseTemplate(
                this.messageService.getOrDefault("prefix", ""),
                null,
                Map.of(),
                Map.of()
        );
        String text = this.chatConfig.configuration().getString(path);

        if (text == null) {
            text = this.messagesConfig.configuration().getString(path, "<red>В конфиге отсутствует сообщение: " + path);
        }

        Component body = parseTemplate(text, null, stringPlaceholders, componentPlaceholders);
        sender.sendMessage(Component.empty().append(prefix).append(body));
    }

    public String getChatString(String path) {
        return this.chatConfig.configuration().getString(path);
    }

    public void clearReplyTarget(Player player) {
        this.replyTracker.removePlayer(player.getUniqueId());
    }

    public void shutdown() {
        this.replyTracker.clearAll();
    }

    private Component createChatLine(Player player, String path, Map<String, String> stringPlaceholders, Map<String, Component> componentPlaceholders) {
        String template = this.chatConfig.configuration().getString(path, "<red>В конфиге отсутствует шаблон чата: " + path);
        return parseTemplate(template, player, stringPlaceholders, componentPlaceholders);
    }

    private Component parsePlayerInput(Player player, String input, boolean publicChat) {
        FileConfiguration config = this.chatConfig.configuration();
        boolean allowMiniMessage = publicChat
                ? config.getBoolean("formatting.input.chat.use-minimessage", true)
                : config.getBoolean("formatting.input.commands.use-minimessage", true);
        boolean allowLegacy = publicChat
                ? config.getBoolean("formatting.input.chat.use-legacy-colors", true)
                : config.getBoolean("formatting.input.commands.use-legacy-colors", true);
        String miniPermission = config.getString("formatting.input.permissions.minimessage", "auriongo.chat.minimessage");
        String legacyPermission = config.getString("formatting.input.permissions.legacy-colors", "auriongo.chat.color");

        String prepared = input;
        prepared = applyPlaceholders(prepared, player);

        if (!allowMiniMessage || !player.hasPermission(miniPermission)) {
            prepared = this.miniMessage.escapeTags(prepared);
        }

        if (allowLegacy && player.hasPermission(legacyPermission)) {
            prepared = LegacyCodeTranslator.toMiniMessage(
                    prepared,
                    config.getBoolean("formatting.legacy-colors.allow-ampersand", true),
                    config.getBoolean("formatting.legacy-colors.allow-section", false)
            );
        }

        String parserMode = config.getString("formatting.parser", "MINI_MESSAGE");

        if ("LEGACY".equalsIgnoreCase(parserMode)) {
            return this.legacySectionSerializer.deserialize(LegacyCodeTranslator.toSectionCodes(
                    prepared,
                    config.getBoolean("formatting.legacy-colors.allow-ampersand", true),
                    config.getBoolean("formatting.legacy-colors.allow-section", false)
            ));
        }

        try {
            return this.miniMessage.deserialize(prepared);
        } catch (Exception exception) {
            return Component.text(input);
        }
    }

    private Component parseTemplate(String template, Player player, Map<String, String> stringPlaceholders, Map<String, Component> componentPlaceholders) {
        if (template == null || template.isEmpty()) {
            return Component.empty();
        }

        FileConfiguration config = this.chatConfig.configuration();
        String parserMode = config.getString("formatting.parser", "MINI_MESSAGE");
        String prepared = applyPlaceholders(template, player);

        if ("LEGACY".equalsIgnoreCase(parserMode)) {
            for (Map.Entry<String, Component> entry : componentPlaceholders.entrySet()) {
                String legacyValue = this.legacySectionSerializer.serialize(entry.getValue());
                prepared = prepared.replace("{" + entry.getKey() + "}", legacyValue);
            }

            for (Map.Entry<String, String> entry : stringPlaceholders.entrySet()) {
                prepared = prepared.replace("{" + entry.getKey() + "}", entry.getValue());
            }

            return this.legacySectionSerializer.deserialize(LegacyCodeTranslator.toSectionCodes(
                    prepared,
                    config.getBoolean("formatting.legacy-colors.allow-ampersand", true),
                    config.getBoolean("formatting.legacy-colors.allow-section", false)
            ));
        }

        List<TagResolver> resolvers = new ArrayList<>();
        String resolvedTemplate = prepared;

        for (Map.Entry<String, String> entry : stringPlaceholders.entrySet()) {
            resolvedTemplate = resolvedTemplate.replace("{" + entry.getKey() + "}", "<" + entry.getKey() + ">");
            resolvers.add(Placeholder.unparsed(entry.getKey(), entry.getValue()));
        }

        for (Map.Entry<String, Component> entry : componentPlaceholders.entrySet()) {
            resolvedTemplate = resolvedTemplate.replace("{" + entry.getKey() + "}", "<" + entry.getKey() + ">");
            resolvers.add(Placeholder.component(entry.getKey(), entry.getValue()));
        }

        resolvedTemplate = LegacyCodeTranslator.toMiniMessage(
                resolvedTemplate,
                config.getBoolean("formatting.legacy-colors.allow-ampersand", true),
                config.getBoolean("formatting.legacy-colors.allow-section", false)
        );

        try {
            return this.miniMessage.deserialize(resolvedTemplate, TagResolver.resolver(resolvers));
        } catch (Exception exception) {
            return Component.text(template);
        }
    }

    private String applyPlaceholders(String input, Player player) {
        boolean papiEnabled = this.chatConfig.configuration().getBoolean("formatting.placeholderapi.enabled", true);

        if (!papiEnabled || player == null) {
            return input;
        }

        return this.placeholderApiBridge.apply(player, input);
    }
}
