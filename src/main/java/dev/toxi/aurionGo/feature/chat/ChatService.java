package dev.toxi.aurionGo.feature.chat;

import dev.toxi.aurionGo.config.ConfigFile;
import dev.toxi.aurionGo.config.StandardConfigs;
import dev.toxi.aurionGo.feature.integration.SuperVanishBridge;
import dev.toxi.aurionGo.feature.player.PlayerProfileService;
import dev.toxi.aurionGo.message.MessageFormatter;
import dev.toxi.aurionGo.shared.AurionContext;
import dev.toxi.aurionGo.storage.player.PlayerIgnoreRepository;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public final class ChatService {

    private final AurionContext context;
    private final ConfigFile chatConfig;
    private final MessageFormatter messageFormatter;
    private final PlayerProfileService playerProfileService;
    private final PlaceholderApiBridge placeholderApiBridge;
    private PlayerIgnoreRepository ignoreRepository;
    private Method interactiveChatMarkSenderMethod;
    private final ReplyTracker replyTracker = new ReplyTracker();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final PlainTextComponentSerializer plainTextSerializer =
        PlainTextComponentSerializer.plainText();
    private final LegacyComponentSerializer legacySectionSerializer =
        LegacyComponentSerializer.legacySection();

    public ChatService(AurionContext context) {
        this.context = context;
        this.chatConfig = context.configManager().require(StandardConfigs.CHAT);
        this.messageFormatter = context
            .serviceRegistry()
            .require(MessageFormatter.class);
        this.playerProfileService = context
            .serviceRegistry()
            .require(PlayerProfileService.class);
        this.placeholderApiBridge = new PlaceholderApiBridge(context.plugin());
        this.interactiveChatMarkSenderMethod =
            resolveInteractiveChatMarkSender();
    }

    public void configurePublicChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        String rawMessage = this.plainTextSerializer.serialize(event.message());
        ChatRoute route = resolveChatRoute(rawMessage);
        String interactiveContent = markInteractiveChatSender(
            sender,
            route.content()
        );
        Component renderedMessage = parsePlayerInput(
            sender,
            interactiveContent,
            true
        );
        event.message(renderedMessage);

        if (!route.global()) {
            double radiusSquared = route.radius() * route.radius();
            boolean foundOtherRecipients = event
                .viewers()
                .stream()
                .filter(Player.class::isInstance)
                .map(Player.class::cast)
                .anyMatch(
                    target ->
                        !target.getUniqueId().equals(sender.getUniqueId()) &&
                        isVisibleLocalRecipient(sender, target, radiusSquared)
                );

            event
                .viewers()
                .removeIf(
                    audience ->
                        audience instanceof Player target &&
                        !isLocalRecipient(sender, target, radiusSquared)
                );

            if (!foundOtherRecipients) {
                sender.sendActionBar(
                    parseTemplate(
                        this.messageFormatter.getOrDefault(
                            "chat.messages.local-no-recipients-actionbar",
                            "<color:#FF4F4F>⌁ Рядом игроков не найдено.</color>"
                        ),
                        sender,
                        Map.of(),
                        Map.of()
                    )
                );
            }
        }

        event.renderer(
            (
                Player source,
                Component sourceDisplayName,
                Component message,
                Audience viewer
            ) ->
                createChatLine(
                    source,
                    route.formatPath(),
                    Map.of("player", source.getName()),
                    Map.of("message", message)
                )
        );

        if (!route.global()) {
            event.setCancelled(true);

            Component chatLine = createChatLine(
                sender,
                route.formatPath(),
                Map.of("player", sender.getName()),
                Map.of("message", renderedMessage)
            );

            for (Player recipient : Bukkit.getOnlinePlayers()) {
                if (
                    isLocalRecipient(
                        sender,
                        recipient,
                        route.radius() * route.radius()
                    )
                ) {
                    recipient.sendMessage(chatLine);
                }
            }
        }
    }

    public Component createJoinMessage(Player player) {
        if (
            !this.chatConfig
                .configuration()
                .getBoolean("join-message.enabled", true) ||
            this.playerProfileService.shouldSuppressJoinMessage(player) ||
            isPlayerHiddenFromPrivateMessaging(player) ||
            this.playerProfileService.hidesJoinLeaveMessages(player)
        ) {
            return null;
        }

        return createChatLine(
            player,
            "chat.join-message.format",
            Map.of(
                "player",
                player.getName(),
                "world",
                player.getWorld().getName()
            ),
            Map.of()
        );
    }

    public Component createQuitMessage(Player player) {
        if (
            !this.chatConfig
                .configuration()
                .getBoolean("quit-message.enabled", true) ||
            this.playerProfileService.shouldSuppressQuitMessage(player) ||
            isPlayerHiddenFromPrivateMessaging(player) ||
            this.playerProfileService.hidesJoinLeaveMessages(player)
        ) {
            return null;
        }

        return createChatLine(
            player,
            "chat.quit-message.format",
            Map.of(
                "player",
                player.getName(),
                "world",
                player.getWorld().getName()
            ),
            Map.of()
        );
    }

    public void broadcastWorldSwitch(Player player, String fromServer, String toServer) {
        if (
            !this.chatConfig
                .configuration()
                .getBoolean("world-switch-message.enabled", true) ||
            isPlayerHiddenFromPrivateMessaging(player) ||
            this.playerProfileService.hidesJoinLeaveMessages(player)
        ) {
            return;
        }

        Component message = createChatLine(
            player,
            "chat.world-switch-message.format",
            Map.of(
                "player",
                player.getName(),
                "from_server",
                fromServer,
                "to_server",
                toServer
            ),
            Map.of()
        );

        for (Player recipient : Bukkit.getOnlinePlayers()) {
            recipient.sendMessage(message);
        }

        this.context.plugin().getServer().getConsoleSender().sendMessage(message);
    }

    public void sendDo(Player sender, String rawInput) {
        Component message = parsePlayerInput(
            sender,
            markInteractiveChatSender(sender, rawInput),
            false
        ).decoration(TextDecoration.ITALIC, true);
        Component rendered = createChatLine(
            sender,
            "chat.formats.do",
            Map.of(
                "player",
                sender.getName(),
                "world",
                sender.getWorld().getName()
            ),
            Map.of("message", message)
        );

        double radius = this.chatConfig
            .configuration()
            .getDouble("commands.do.radius", 100.0D);
        double radiusSquared = radius * radius;

        for (Player target : sender.getWorld().getPlayers()) {
            if (
                target.getLocation().distanceSquared(sender.getLocation()) <=
                radiusSquared
            ) {
                target.sendMessage(rendered);
            }
        }

        this.context
            .plugin()
            .getServer()
            .getConsoleSender()
            .sendMessage(rendered);
    }

    public boolean sendPrivateMessage(
        Player sender,
        Player target,
        String rawInput
    ) {
        if (isPlayerHiddenFromPrivateMessaging(target)) {
            sendChatNotice(
                sender,
                "chat.messages.player-not-found",
                Map.of("target", target.getName()),
                Map.of()
            );
            return false;
        }

        Component message = parsePlayerInput(
            sender,
            markInteractiveChatSender(sender, rawInput),
            false
        );

        if (sender.getUniqueId().equals(target.getUniqueId())) {
            Component note = createChatLine(
                sender,
                "chat.formats.private-message-self",
                Map.of("player", sender.getName()),
                Map.of("message", message)
            );
            sender.sendMessage(note);
            return true;
        }

        Component sent = createChatLine(
            sender,
            "chat.formats.private-message-sent",
            Map.of("player", sender.getName(), "target", target.getName()),
            Map.of("message", message)
        );
        Component received = createChatLine(
            target,
            "chat.formats.private-message-received",
            Map.of("player", sender.getName(), "target", target.getName()),
            Map.of(
                "message",
                message,
                "player",
                createPlayerMentionComponent(sender, sender.getName())
            )
        );

        sender.sendMessage(sent);
        target.sendMessage(received);
        notifySpyWatchers(sender, target, message);
        playPrivateMessageSound(target);
        this.replyTracker.link(sender.getUniqueId(), target.getUniqueId());
        return true;
    }

    public void broadcastAnnouncement(CommandSender sender, String rawInput) {
        Component message = parseAnnouncementMessage(sender, rawInput);
        Component rendered = parseTemplate(
            this.messageFormatter.getOrDefault(
                "chat.formats.broadcast",
                "<gold>Bell</gold> <fcolor:2>Объявление:</fcolor:2> <message>"
            ),
            sender instanceof Player player ? player : null,
            Map.of(),
            Map.of("message", message)
        );

        for (Player recipient : Bukkit.getOnlinePlayers()) {
            recipient.sendMessage(rendered);
        }

        this.context.plugin().getServer().getConsoleSender().sendMessage(rendered);
    }

    public boolean sendReply(Player sender, String rawInput) {
        UUID replyTargetId = this.replyTracker.getReplyTarget(
            sender.getUniqueId()
        );

        if (replyTargetId == null) {
            sendChatNotice(
                sender,
                "chat.messages.reply-target-missing",
                Map.of(),
                Map.of()
            );
            return false;
        }

        Player target = Bukkit.getPlayer(replyTargetId);

        if (target == null || isPlayerHiddenFromPrivateMessaging(target)) {
            this.replyTracker.clear(sender.getUniqueId());
            sendChatNotice(
                sender,
                "chat.messages.reply-target-offline",
                Map.of(),
                Map.of()
            );
            return false;
        }

        return sendPrivateMessage(sender, target, rawInput);
    }

    public Player findOnlinePlayer(String input) {
        Player player = Bukkit.getPlayer(input);
        return player != null && !isPlayerHiddenFromPrivateMessaging(player)
            ? player
            : null;
    }

    public void sendChatNotice(
        CommandSender sender,
        String path,
        Map<String, String> stringPlaceholders,
        Map<String, Component> componentPlaceholders
    ) {
        Component prefix = parseTemplate(
            this.messageFormatter.getOrDefault("prefix", ""),
            null,
            Map.of(),
            Map.of()
        );
        String text = this.messageFormatter.getOrDefault(
            path,
            "<red>В конфиге отсутствует сообщение: " + path
        );
        Component body = parseTemplate(
            text,
            null,
            stringPlaceholders,
            componentPlaceholders
        );
        sender.sendMessage(Component.empty().append(prefix).append(body));
    }

    public void clearReplyTarget(Player player) {
        this.replyTracker.removePlayer(player.getUniqueId());
    }

    public boolean toggleSpy(Player player) {
        return this.playerProfileService.toggleSpy(player);
    }

    public void shutdown() {
        this.replyTracker.clearAll();
    }

    private boolean isPlayerHiddenFromPrivateMessaging(Player player) {
        return SuperVanishBridge.isVanished(this.context.plugin(), player);
    }

    private void playPrivateMessageSound(Player target) {
        FileConfiguration config = this.chatConfig.configuration();

        if (!config.getBoolean("private-message.sound.enabled", true)) {
            return;
        }

        String soundKey = config.getString(
            "private-message.sound.type",
            "block.note_block.bell"
        );

        if (soundKey == null || soundKey.isEmpty()) {
            return;
        }

        float volume = (float) config.getDouble(
            "private-message.sound.volume",
            1.0D
        );
        float pitch = (float) config.getDouble(
            "private-message.sound.pitch",
            1.0D
        );

        try {
            Sound sound = Sound.sound(
                Key.key(soundKey),
                Sound.Source.MASTER,
                volume,
                pitch
            );
            target.playSound(sound);
        } catch (Exception exception) {
            this.context
                .plugin()
                .getLogger()
                .warning(
                    "Не удалось воспроизвести звук ЛС \"" +
                        soundKey +
                        "\": " +
                        exception.getMessage()
                );
        }
    }

    private void notifySpyWatchers(
        Player sender,
        Player target,
        Component message
    ) {
        Component spyLine = createChatLine(
            sender,
            "chat.formats.spy",
            Map.of("sender", sender.getName(), "target", target.getName()),
            Map.of(
                "message", message,
                "sender", createPlayerMentionComponent(sender, sender.getName()),
                "target", createPlayerMentionComponent(target, target.getName())
            )
        );

        for (Player watcher : Bukkit.getOnlinePlayers()) {
            if (watcher.getUniqueId().equals(sender.getUniqueId())) {
                continue;
            }

            if (watcher.getUniqueId().equals(target.getUniqueId())) {
                continue;
            }

            if (!watcher.hasPermission("auriongo.command.chat.spy")) {
                continue;
            }

            if (!this.playerProfileService.isSpyEnabled(watcher)) {
                continue;
            }

            watcher.sendMessage(spyLine);
        }
    }

    private Component createChatLine(
        Player player,
        String path,
        Map<String, String> stringPlaceholders,
        Map<String, Component> componentPlaceholders
    ) {
        String template = this.messageFormatter.getOrDefault(
            path,
            "<red>В конфиге отсутствует шаблон чата: " + path
        );

        Map<String, String> resolvedStringPlaceholders = stringPlaceholders;
        Map<String, Component> resolvedComponentPlaceholders =
            new java.util.HashMap<>(componentPlaceholders);

        if (
            player != null &&
            stringPlaceholders.containsKey("player") &&
            !resolvedComponentPlaceholders.containsKey("player")
        ) {
            resolvedStringPlaceholders = new java.util.HashMap<>(
                stringPlaceholders
            );
            resolvedStringPlaceholders.remove("player");
            resolvedComponentPlaceholders.put(
                "player",
                createPlayerMentionComponent(
                    player,
                    stringPlaceholders.get("player")
                )
            );
        }

        return parseTemplate(
            template,
            player,
            resolvedStringPlaceholders,
            resolvedComponentPlaceholders
        );
    }

    private Component createPlayerMentionComponent(
        Player player,
        String displayName
    ) {
        Component hover = this.messageFormatter.render(
            "chat.messages.player-hover-write",
            Map.of("player", displayName)
        );

        return Component.text(displayName)
            .hoverEvent(HoverEvent.showText(hover))
            .clickEvent(
                ClickEvent.suggestCommand("/msg " + player.getName() + " ")
            );
    }

    private Component parsePlayerInput(
        Player player,
        String input,
        boolean publicChat
    ) {
        FileConfiguration config = this.chatConfig.configuration();
        boolean allowMiniMessage = publicChat
            ? config.getBoolean("formatting.input.chat.use-minimessage", true)
            : config.getBoolean(
                  "formatting.input.commands.use-minimessage",
                  true
              );
        boolean allowLegacy = publicChat
            ? config.getBoolean("formatting.input.chat.use-legacy-colors", true)
            : config.getBoolean(
                  "formatting.input.commands.use-legacy-colors",
                  true
              );
        String miniPermission = config.getString(
            "formatting.input.permissions.minimessage",
            "auriongo.command.chat.minimessage"
        );
        String legacyPermission = config.getString(
            "formatting.input.permissions.legacy-colors",
            "auriongo.command.chat.color"
        );

        String prepared = input;
        prepared = applyPlaceholders(prepared, player);

        if (!allowMiniMessage || !player.hasPermission(miniPermission)) {
            prepared = this.miniMessage.escapeTags(prepared);
        }

        if (allowLegacy && player.hasPermission(legacyPermission)) {
            prepared = LegacyCodeTranslator.toMiniMessage(
                prepared,
                config.getBoolean(
                    "formatting.legacy-colors.allow-ampersand",
                    true
                ),
                config.getBoolean(
                    "formatting.legacy-colors.allow-section",
                    false
                )
            );
        }

        String parserMode = config.getString(
            "formatting.parser",
            "MINI_MESSAGE"
        );

        if ("LEGACY".equalsIgnoreCase(parserMode)) {
            return this.legacySectionSerializer.deserialize(
                LegacyCodeTranslator.toSectionCodes(
                    prepared,
                    config.getBoolean(
                        "formatting.legacy-colors.allow-ampersand",
                        true
                    ),
                    config.getBoolean(
                        "formatting.legacy-colors.allow-section",
                        false
                    )
                )
            );
        }

        try {
            return this.miniMessage.deserialize(
                applyPlaceholders(prepared, player)
            );
        } catch (Exception exception) {
            return Component.text(applyPlaceholders(input, player));
        }
    }

    private Component parseAnnouncementMessage(CommandSender sender, String input) {
        if (sender instanceof Player player) {
            return parsePlayerInput(player, markInteractiveChatSender(player, input), false);
        }

        return parseTemplate(input, null, Map.of(), Map.of());
    }

    private Component parseTemplate(
        String template,
        Player player,
        Map<String, String> stringPlaceholders,
        Map<String, Component> componentPlaceholders
    ) {
        if (template == null || template.isEmpty()) {
            return Component.empty();
        }

        FileConfiguration config = this.chatConfig.configuration();
        String parserMode = config.getString(
            "formatting.parser",
            "MINI_MESSAGE"
        );
        String prepared = this.messageFormatter.resolveTemplate(
            applyPlaceholders(template, player)
        );

        if ("LEGACY".equalsIgnoreCase(parserMode)) {
            for (Map.Entry<
                String,
                Component
            > entry : componentPlaceholders.entrySet()) {
                String legacyValue = this.legacySectionSerializer.serialize(
                    entry.getValue()
                );
                prepared = prepared.replace(
                    "{" + entry.getKey() + "}",
                    legacyValue
                );
            }

            for (Map.Entry<
                String,
                String
            > entry : stringPlaceholders.entrySet()) {
                prepared = prepared.replace(
                    "{" + entry.getKey() + "}",
                    entry.getValue()
                );
            }

            return this.legacySectionSerializer.deserialize(
                LegacyCodeTranslator.toSectionCodes(
                    prepared,
                    config.getBoolean(
                        "formatting.legacy-colors.allow-ampersand",
                        true
                    ),
                    config.getBoolean(
                        "formatting.legacy-colors.allow-section",
                        false
                    )
                )
            );
        }

        List<TagResolver> resolvers = new ArrayList<>();
        String resolvedTemplate = prepared;

        for (Map.Entry<String, String> entry : stringPlaceholders.entrySet()) {
            resolvedTemplate = resolvedTemplate.replace(
                "{" + entry.getKey() + "}",
                "<" + entry.getKey() + ">"
            );
            resolvers.add(
                Placeholder.unparsed(entry.getKey(), entry.getValue())
            );
        }

        for (Map.Entry<
            String,
            Component
        > entry : componentPlaceholders.entrySet()) {
            resolvedTemplate = resolvedTemplate.replace(
                "{" + entry.getKey() + "}",
                "<" + entry.getKey() + ">"
            );
            resolvers.add(
                Placeholder.component(entry.getKey(), entry.getValue())
            );
        }

        resolvedTemplate = LegacyCodeTranslator.toMiniMessage(
            resolvedTemplate,
            config.getBoolean("formatting.legacy-colors.allow-ampersand", true),
            config.getBoolean("formatting.legacy-colors.allow-section", false)
        );

        try {
            return this.miniMessage.deserialize(
                resolvedTemplate,
                TagResolver.resolver(resolvers)
            );
        } catch (Exception exception) {
            return Component.text(template);
        }
    }

    private String applyPlaceholders(String input, Player player) {
        boolean papiEnabled = this.chatConfig
            .configuration()
            .getBoolean("formatting.placeholderapi.enabled", true);

        if (player == null) {
            return input;
        }

        String resolved = input;

        if (papiEnabled) {
            resolved = this.placeholderApiBridge.apply(player, resolved);
        }

        return applyBuiltinPlayerPlaceholders(resolved, player);
    }

    private String applyBuiltinPlayerPlaceholders(String input, Player player) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        return input
            .replace("%player_name%", player.getName())
            .replace(
                "%player_displayname%",
                this.plainTextSerializer.serialize(player.displayName())
            )
            .replace("%player_uuid%", player.getUniqueId().toString());
    }

    private String markInteractiveChatSender(Player player, String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        Method method = this.interactiveChatMarkSenderMethod;

        if (method == null) {
            return input;
        }

        try {
            Object result = method.invoke(null, input, player.getUniqueId());
            return result instanceof String string ? string : input;
        } catch (Exception exception) {
            return input;
        }
    }

    private Method resolveInteractiveChatMarkSender() {
        if (
            this.context
                .plugin()
                .getServer()
                .getPluginManager()
                .getPlugin("InteractiveChat") == null
        ) {
            return null;
        }

        try {
            Class<?> apiClass = Class.forName(
                "com.loohp.interactivechat.api.InteractiveChatAPI"
            );
            return apiClass.getMethod("markSender", String.class, UUID.class);
        } catch (Exception exception) {
            return null;
        }
    }

    private ChatRoute resolveChatRoute(String rawMessage) {
        FileConfiguration config = this.chatConfig.configuration();
        boolean globalEnabled = config.getBoolean(
            "channels.global.enabled",
            true
        );
        String globalPrefix = config.getString("channels.global.prefix", "!");

        if (
            globalEnabled &&
            globalPrefix != null &&
            !globalPrefix.isEmpty() &&
            rawMessage.startsWith(globalPrefix)
        ) {
            String stripped = rawMessage
                .substring(globalPrefix.length())
                .stripLeading();

            if (!stripped.isEmpty()) {
                return new ChatRoute(
                    true,
                    stripped,
                    "chat.channels.global.format",
                    0.0D
                );
            }
        }

        return new ChatRoute(
            false,
            rawMessage,
            "chat.channels.local.format",
            config.getDouble("channels.local.radius", 150.0D)
        );
    }

    private boolean isLocalRecipient(
        Player sender,
        Player target,
        double radiusSquared
    ) {
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            return true;
        }

        if (!sender.getWorld().equals(target.getWorld())) {
            return false;
        }

        return (
            sender.getLocation().distanceSquared(target.getLocation()) <=
            radiusSquared
        );
    }

    private boolean isVisibleLocalRecipient(
        Player sender,
        Player target,
        double radiusSquared
    ) {
        return (
            isLocalRecipient(sender, target, radiusSquared) &&
            isRecipientVisibleToSender(sender, target)
        );
    }

    private boolean isRecipientVisibleToSender(Player sender, Player target) {
        return !SuperVanishBridge.isHiddenFrom(
            this.context.plugin(),
            sender,
            target
        );
    }

    private record ChatRoute(
        boolean global,
        String content,
        String formatPath,
        double radius
    ) {}
}
