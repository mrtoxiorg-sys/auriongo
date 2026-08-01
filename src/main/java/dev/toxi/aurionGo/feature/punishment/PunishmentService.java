package dev.toxi.aurionGo.feature.punishment;

import dev.toxi.aurionGo.message.MessageFormatter;
import dev.toxi.aurionGo.shared.AurionContext;
import dev.toxi.aurionGo.storage.player.PlayerProfileRecord;
import dev.toxi.aurionGo.storage.player.PlayerProfileRepository;
import dev.toxi.aurionGo.storage.punishment.PunishmentCreateRequest;
import dev.toxi.aurionGo.storage.punishment.PunishmentPage;
import dev.toxi.aurionGo.storage.punishment.PunishmentRecord;
import dev.toxi.aurionGo.storage.punishment.PunishmentRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public final class PunishmentService {

    private static final long MUTE_CACHE_TTL_MILLIS = 5_000L;
    private final AurionContext context;
    private final PunishmentRepository punishmentRepository;
    private final PlayerProfileRepository playerProfileRepository;
    private final MessageFormatter messageFormatter;
    private final ConcurrentMap<UUID, CachedMuteState> muteCache =
        new ConcurrentHashMap<>();

    public PunishmentService(
        AurionContext context,
        PunishmentRepository punishmentRepository,
        PlayerProfileRepository playerProfileRepository
    ) {
        this.context = context;
        this.punishmentRepository = punishmentRepository;
        this.playerProfileRepository = playerProfileRepository;
        this.messageFormatter = context
            .serviceRegistry()
            .require(MessageFormatter.class);
    }

    public void applyBan(
        CommandSender sender,
        String targetInput,
        String reason,
        Long durationMillis,
        boolean silent
    ) {
        applyPersistentPunishment(
            sender,
            PunishmentType.BAN,
            targetInput,
            reason,
            durationMillis,
            false,
            silent
        );
    }

    public void applyMute(
        CommandSender sender,
        String targetInput,
        String reason,
        Long durationMillis,
        boolean silent
    ) {
        applyPersistentPunishment(
            sender,
            PunishmentType.MUTE,
            targetInput,
            reason,
            durationMillis,
            true,
            silent
        );
    }

    public void applyWarn(
        CommandSender sender,
        String targetInput,
        String reason,
        Long durationMillis,
        boolean silent
    ) {
        applyPersistentPunishment(
            sender,
            PunishmentType.WARN,
            targetInput,
            reason,
            durationMillis,
            false,
            silent
        );
    }

    public void applyKick(
        CommandSender sender,
        String targetInput,
        String reason,
        boolean silent
    ) {
        PunishmentTarget target = resolveTarget(targetInput);

        if (target == null) {
            sender.sendMessage(
                renderFromPunishments(
                    "messages.target-not-found",
                    Map.of("target", targetInput)
                )
            );
            return;
        }

        if (target.onlinePlayer() == null) {
            sender.sendMessage(
                renderFromPunishments(
                    "messages.target-must-be-online",
                    Map.of("target", target.nickname())
                )
            );
            return;
        }

        if (isSelfTarget(sender, target.uuid())) {
            sender.sendMessage(
                renderFromPunishments("messages.cannot-target-self", Map.of())
            );
            return;
        }

        try {
            long now = System.currentTimeMillis();
            PunishmentRecord record = createRecord(
                sender,
                PunishmentType.KICK,
                target,
                reason,
                null,
                false,
                now
            );
            notifyPunishmentApplied(sender, target, record);
            if (!silent) {
                broadcastPunishment(record);
            }

            if (target.onlinePlayer() != null) {
                target
                    .onlinePlayer()
                    .kick(renderBanLikeScreen("screens.kick-screen", record));
            }
        } catch (Exception exception) {
            sender.sendMessage(
                renderFromPunishments(
                    "messages.lookup-error",
                    Map.of("error", exception.getMessage())
                )
            );
        }
    }

    public void listPunishments(
        CommandSender sender,
        PunishmentType type,
        int page
    ) {
        try {
            PunishmentPage punishmentPage =
                this.punishmentRepository.listActive(
                    type,
                    page,
                    pageSize(),
                    System.currentTimeMillis()
                );

            if (punishmentPage.entries().isEmpty()) {
                sender.sendMessage(
                    renderFromPunishments(
                        "lists.empty",
                        Map.of("type", typeDisplayPlural(type))
                    )
                );
                return;
            }

            sender.sendMessage(
                renderFromPunishments(
                    "lists.header",
                    Map.of(
                        "type",
                        typeDisplayPlural(type),
                        "page",
                        Integer.toString(punishmentPage.page()),
                        "pages",
                        Integer.toString(punishmentPage.totalPages()),
                        "count",
                        Integer.toString(punishmentPage.totalEntries())
                    )
                )
            );

            for (PunishmentRecord record : punishmentPage.entries()) {
                sender.sendMessage(renderPunishmentListEntry(type, record));
            }

            sender.sendMessage(
                renderPunishmentListNavigation(
                    type,
                    punishmentPage.page(),
                    punishmentPage.totalPages()
                )
            );
        } catch (Exception exception) {
            sender.sendMessage(
                renderFromPunishments(
                    "messages.lookup-error",
                    Map.of("error", exception.getMessage())
                )
            );
        }
    }

    public void searchPunishments(
        CommandSender sender,
        PunishmentType type,
        String query,
        int page
    ) {
        try {
            PunishmentPage punishmentPage =
                this.punishmentRepository.searchByNickname(
                    type,
                    query,
                    page,
                    pageSize(),
                    System.currentTimeMillis()
                );

            if (punishmentPage.entries().isEmpty()) {
                sender.sendMessage(
                    renderFromPunishments(
                        "lists.search-empty",
                        Map.of("type", typeDisplayPlural(type), "query", query)
                    )
                );
                return;
            }

            sender.sendMessage(
                renderFromPunishments(
                    "lists.search-header",
                    Map.of(
                        "type",
                        typeDisplayPlural(type),
                        "query",
                        query,
                        "page",
                        String.valueOf(punishmentPage.page()),
                        "pages",
                        String.valueOf(punishmentPage.totalPages()),
                        "count",
                        String.valueOf(punishmentPage.totalEntries())
                    )
                )
            );

            for (PunishmentRecord record : punishmentPage.entries()) {
                sender.sendMessage(renderPunishmentListEntry(type, record));
            }

            sender.sendMessage(
                renderPunishmentListSearchNavigation(
                    type,
                    query,
                    punishmentPage.page(),
                    punishmentPage.totalPages()
                )
            );
        } catch (Exception exception) {
            sender.sendMessage(
                renderFromPunishments(
                    "messages.lookup-error",
                    Map.of("error", exception.getMessage())
                )
            );
        }
    }

    public void removePunishment(
        CommandSender sender,
        PunishmentType type,
        String query
    ) {
        long now = System.currentTimeMillis();
        UUID moderatorUuid = sender instanceof Player player
            ? player.getUniqueId()
            : null;
        String moderatorName = sender.getName();

        try {
            boolean success;
            UUID affectedTarget = null;
            String targetDisplayName = query;

            if (query.matches("\\d+")) {
                Optional<PunishmentRecord> record =
                    this.punishmentRepository.findById(Long.parseLong(query));
                affectedTarget = record
                    .map(PunishmentRecord::targetUuid)
                    .orElse(null);
                targetDisplayName = record
                    .map(PunishmentRecord::targetNickname)
                    .orElse(query);
                success = this.punishmentRepository.deactivateById(
                    type,
                    Long.parseLong(query),
                    moderatorUuid,
                    moderatorName,
                    "Снято вручную",
                    now
                );
            } else {
                PunishmentTarget target = resolveTarget(query);

                if (target == null) {
                    sender.sendMessage(
                        renderFromPunishments(
                            "messages.target-not-found",
                            Map.of("target", query)
                        )
                    );
                    return;
                }

                affectedTarget = target.uuid();
                targetDisplayName = target.nickname();
                success = this.punishmentRepository.deactivateLatestByTarget(
                    type,
                    target.uuid(),
                    moderatorUuid,
                    moderatorName,
                    "Снято вручную",
                    now,
                    now
                );
            }

            if (!success) {
                sender.sendMessage(
                    renderFromPunishments(
                        "messages.punishment-not-found",
                        Map.of("type", typeDisplay(type))
                    )
                );
                return;
            }

            if (affectedTarget != null) {
                refreshPlayerState(affectedTarget);
            }

            notifyPunishmentRemoved(sender, type, targetDisplayName);
            broadcastPunishmentRemove(type, moderatorName, targetDisplayName);
        } catch (Exception exception) {
            sender.sendMessage(
                renderFromPunishments(
                    "messages.lookup-error",
                    Map.of("error", exception.getMessage())
                )
            );
        }
    }

    public Component createBanScreen(UUID targetUuid) {
        try {
            PunishmentRecord activeBan = activePunishment(PunishmentType.BAN, targetUuid);

            if (activeBan == null) {
                return null;
            }

            return renderFromPunishments(
                "screens.ban",
                Map.of(
                    "id",
                    Long.toString(activeBan.id()),
                    "moderator",
                    activeBan.moderatorName(),
                    "reason",
                    activeBan.reason(),
                    "expires",
                    formatRemainingDuration(activeBan.expiresAt())
                )
            );
        } catch (Exception exception) {
            this.context
                .plugin()
                .getLogger()
                .warning(
                    "Не удалось проверить бан при входе: " +
                        exception.getMessage()
                );
            return null;
        }
    }

    public void notifyBanJoinAttempt(String playerName, UUID targetUuid) {
        try {
            PunishmentRecord activeBan = activePunishment(PunishmentType.BAN, targetUuid);

            if (activeBan == null) {
                return;
            }

            Component details = renderPunishmentDetails(activeBan);
            Component more = renderFromPunishments(
                "broadcasts.more",
                Map.of()
            ).hoverEvent(HoverEvent.showText(details));
            Component base = renderFromPunishments(
                "messages.login-ban-attempt",
                Map.of("player", playerName)
            );
            Component message = Component.empty()
                .append(base)
                .append(Component.space())
                .append(more);

            this.context
                .plugin()
                .getServer()
                .getScheduler()
                .runTask(this.context.plugin(), task -> {
                    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                        if (
                            onlinePlayer.hasPermission(
                                "auriongo.command.punishment.ban"
                            )
                        ) {
                            onlinePlayer.sendMessage(message);
                        }
                    }

                    Bukkit.getConsoleSender().sendMessage(message);
                });
        } catch (Exception exception) {
            this.context
                .plugin()
                .getLogger()
                .warning(
                    "Не удалось отправить уведомление о попытке входа забаненного игрока: " +
                    exception.getMessage()
                );
        }
    }

    public Component createMuteBlockMessage(UUID targetUuid) {
        try {
            PunishmentRecord activeMute = findActiveMute(targetUuid);

            if (activeMute == null) {
                return null;
            }

            return renderFromPunishments(
                "messages.mute-blocked",
                Map.of(
                    "id",
                    Long.toString(activeMute.id()),
                    "reason",
                    activeMute.reason(),
                    "expires",
                    formatRemainingDuration(activeMute.expiresAt())
                )
            );
        } catch (Exception exception) {
            this.context
                .plugin()
                .getLogger()
                .warning(
                    "Не удалось проверить мут игрока: " + exception.getMessage()
                );
            return null;
        }
    }

    public void sendMuteBlockMessage(Player player, Component component) {
        this.context
            .plugin()
            .getServer()
            .getScheduler()
            .runTask(this.context.plugin(), task ->
                player.sendActionBar(component)
            );
    }

    public boolean hasActiveMute(UUID targetUuid) {
        try {
            return findActiveMute(targetUuid) != null;
        } catch (Exception exception) {
            this.context
                .plugin()
                .getLogger()
                .warning(
                    "Не удалось проверить мут игрока: " + exception.getMessage()
                );
            return false;
        }
    }

    public boolean shouldBlockMutedCommand(UUID targetUuid, String rawCommand) {
        if (!hasActiveMute(targetUuid)) {
            return false;
        }

        String normalized = rawCommand.startsWith("/")
            ? rawCommand.substring(1)
            : rawCommand;
        String label = normalized.split("\\s+", 2)[0].toLowerCase();
        List<String> blockedCommands = config().getStringList(
            "mutes.blocked-commands"
        );
        return blockedCommands
            .stream()
            .map(String::toLowerCase)
            .anyMatch(label::equals);
    }

    public Component renderNoPermission() {
        return renderFromMessages("errors.no-permission", Map.of());
    }

    public Component renderUsage(PunishmentType type) {
        return renderFromPunishments(
            "commands." + type.key() + ".usage",
            Map.of()
        );
    }

    public Component renderRemoveUsage(PunishmentType type) {
        return renderFromPunishments(
            "commands.un" + type.key() + ".usage",
            Map.of()
        );
    }

    public Component renderListSearchUsage(PunishmentType type) {
        return renderFromPunishments(
            "commands." + type.key() + "search.usage",
            Map.of()
        );
    }

    public List<String> suggestActiveTargets(
        PunishmentType type,
        String prefix
    ) {
        try {
            return this.punishmentRepository.findActiveTargetNames(
                type,
                prefix,
                15,
                System.currentTimeMillis()
            );
        } catch (Exception exception) {
            return List.of();
        }
    }

    private void applyPersistentPunishment(
        CommandSender sender,
        PunishmentType type,
        String targetInput,
        String reason,
        Long durationMillis,
        boolean requireReason,
        boolean silent
    ) {
        PunishmentTarget target = resolveTarget(targetInput);

        if (target == null) {
            sender.sendMessage(
                renderFromPunishments(
                    "messages.target-not-found",
                    Map.of("target", targetInput)
                )
            );
            return;
        }

        if (isSelfTarget(sender, target.uuid())) {
            sender.sendMessage(
                renderFromPunishments("messages.cannot-target-self", Map.of())
            );
            return;
        }

        if (requireReason && (reason == null || reason.isBlank())) {
            sender.sendMessage(
                renderFromPunishments(
                    "messages.reason-required",
                    Map.of("type", typeDisplay(type))
                )
            );
            return;
        }

        try {
            long now = System.currentTimeMillis();
            PunishmentRecord existing = activePunishment(type, target.uuid());

            if (existing != null && type != PunishmentType.WARN) {
                sender.sendMessage(
                    renderFromPunishments(
                        "messages.already-active",
                        Map.of(
                            "type",
                            typeDisplay(type),
                            "target",
                            target.nickname()
                        )
                    )
                );
                return;
            }

            Long expiresAt =
                durationMillis == null ? null : now + durationMillis;
            PunishmentRecord record = createRecord(
                sender,
                type,
                target,
                reason,
                expiresAt,
                type != PunishmentType.KICK,
                now
            );
            refreshPlayerState(target.uuid());
            notifyPunishmentApplied(sender, target, record);
            if (!silent) {
                broadcastPunishment(record);
            }

            if (type == PunishmentType.BAN && target.onlinePlayer() != null) {
                target
                    .onlinePlayer()
                    .kick(renderBanLikeScreen("screens.ban", record));
            }
        } catch (Exception exception) {
            sender.sendMessage(
                renderFromPunishments(
                    "messages.lookup-error",
                    Map.of("error", exception.getMessage())
                )
            );
        }
    }

    private PunishmentRecord createRecord(
        CommandSender sender,
        PunishmentType type,
        PunishmentTarget target,
        String reason,
        Long expiresAt,
        boolean active,
        long now
    ) throws Exception {
        UUID moderatorUuid = sender instanceof Player player
            ? player.getUniqueId()
            : null;
        String moderatorName = sender instanceof ConsoleCommandSender
            ? "CONSOLE"
            : sender.getName();
        String finalReason = (reason == null || reason.isBlank())
            ? this.messageFormatter.getOrDefault(
                  "punishments.defaults.reason",
                  "Не указана"
              )
            : reason;

        PunishmentCreateRequest request = new PunishmentCreateRequest(
            type,
            target.uuid(),
            target.nickname(),
            moderatorUuid,
            moderatorName,
            finalReason,
            now,
            expiresAt,
            active
        );

        return this.punishmentRepository.create(request);
    }

    private void broadcastPunishment(PunishmentRecord record) {
        if (
            !config().getBoolean(
                "broadcasts." + record.type().key() + ".enabled",
                true
            )
        ) {
            return;
        }

        Component details = renderPunishmentDetails(record);

        Component more = renderFromPunishments(
            "broadcasts.more",
            Map.of()
        ).hoverEvent(HoverEvent.showText(details));

        Component base = renderFromPunishments(
            "broadcasts." + record.type().key() + ".format",
            Map.of(
                "moderator",
                record.moderatorName(),
                "target",
                record.targetNickname()
            )
        );

        sendBroadcast(
            Component.empty()
                .append(base)
                .append(Component.space())
                .append(more)
        );
    }

    private void broadcastPunishmentRemove(
        PunishmentType type,
        String moderatorName,
        String targetName
    ) {
        String removeKey = "un" + type.key();

        if (
            !config().getBoolean("broadcasts." + removeKey + ".enabled", true)
        ) {
            return;
        }

        Component base = renderFromPunishments(
            "broadcasts." + removeKey + ".format",
            Map.of("moderator", moderatorName, "target", targetName)
        );

        sendBroadcast(base);
    }

    private void sendBroadcast(Component message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
        }

        Bukkit.getConsoleSender().sendMessage(message);
    }

    private void notifyPunishmentApplied(
        CommandSender sender,
        PunishmentTarget target,
        PunishmentRecord record
    ) {
        sender.sendMessage(
            renderFromPunishments(
                "messages.applied",
                Map.of(
                    "type",
                    typeDisplay(record.type()),
                    "target",
                    record.targetNickname(),
                    "expires",
                    formatAppliedDuration(record)
                )
            )
        );

        if (
            target.onlinePlayer() != null && record.type() != PunishmentType.BAN
        ) {
            target
                .onlinePlayer()
                .sendMessage(
                    renderFromPunishments(
                        "messages.target-notified",
                        Map.of(
                            "type",
                            typeDisplay(record.type()),
                            "moderator",
                            record.moderatorName(),
                            "reason",
                            record.reason(),
                            "expires",
                            formatAppliedDuration(record)
                        )
                    )
                );
        }
    }

    private void notifyPunishmentRemoved(
        CommandSender sender,
        PunishmentType type,
        String targetName
    ) {
        sender.sendMessage(
            renderFromPunishments(
                "messages.removed",
                Map.of("type", typeDisplay(type), "query", targetName)
            )
        );
    }

    private PunishmentTarget resolveTarget(String input) {
        Player onlinePlayer = Bukkit.getPlayerExact(input);

        if (onlinePlayer != null) {
            return new PunishmentTarget(
                onlinePlayer.getUniqueId(),
                onlinePlayer.getName(),
                onlinePlayer
            );
        }

        try {
            Optional<PlayerProfileRecord> profile =
                this.playerProfileRepository.findByNickname(input);

            if (profile.isEmpty()) {
                return null;
            }

            return new PunishmentTarget(
                profile.get().uuid(),
                profile.get().nickname(),
                null
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                "Не удалось найти игрока " + input,
                exception
            );
        }
    }

    private PunishmentRecord activePunishment(
        PunishmentType type,
        UUID targetUuid
    ) throws Exception {
        long now = System.currentTimeMillis();
        int expired = this.punishmentRepository.deactivateExpired(
            type,
            targetUuid,
            now
        );

        if (expired > 0) {
            refreshPlayerState(targetUuid);
        }

        return this.punishmentRepository
            .findActiveByTypeAndTarget(type, targetUuid, now)
            .orElse(null);
    }

    private PunishmentRecord findActiveMute(UUID targetUuid) throws Exception {
        long now = System.currentTimeMillis();
        CachedMuteState cached = this.muteCache.get(targetUuid);

        if (cached != null) {
            if (
                cached.record() != null &&
                (cached.record().expiresAt() == null ||
                    cached.record().expiresAt() > now)
            ) {
                return cached.record();
            }

            if (
                cached.record() == null &&
                now - cached.checkedAt() < MUTE_CACHE_TTL_MILLIS
            ) {
                return null;
            }
        }

        PunishmentRecord activeMute = activePunishment(
            PunishmentType.MUTE,
            targetUuid
        );
        this.muteCache.put(targetUuid, new CachedMuteState(activeMute, now));
        return activeMute;
    }

    private Component renderPunishmentListEntry(
        PunishmentType type,
        PunishmentRecord record
    ) {
        Component target = renderFromPunishments(
            "lists.entry-target",
            Map.of("target", record.targetNickname())
        );
        Component detailsButton = renderFromPunishments(
            "lists.entry-more",
            Map.of()
        ).hoverEvent(HoverEvent.showText(renderPunishmentListDetails(record)));

        return Component.empty()
            .append(target)
            .append(Component.space())
            .append(detailsButton);
    }

    private Component renderPunishmentListDetails(PunishmentRecord record) {
        return renderFromPunishments(
            "lists.entry-hover",
            Map.of(
                "id",
                Long.toString(record.id()),
                "target",
                record.targetNickname(),
                "moderator",
                record.moderatorName(),
                "reason",
                record.reason(),
                "expires",
                formatRemainingDuration(record.expiresAt()),
                "type",
                typeDisplay(record.type())
            )
        );
    }

    private Component renderPunishmentListNavigation(
        PunishmentType type,
        int page,
        int totalPages
    ) {
        String commandBase = switch (type) {
            case BAN -> "/banlist ";
            case MUTE -> "/mutelist ";
            case WARN -> "/warnlist ";
            default -> null;
        };

        if (commandBase == null || totalPages <= 1) {
            return Component.empty();
        }

        Component previous =
            page > 1
                ? renderFromPunishments(
                      "lists.navigation.previous-active",
                      Map.of()
                  )
                      .clickEvent(
                          ClickEvent.runCommand(commandBase + (page - 1))
                      )
                      .hoverEvent(
                          HoverEvent.showText(
                              renderFromPunishments(
                                  "lists.navigation.previous-hover",
                                  Map.of("page", Integer.toString(page - 1))
                              )
                          )
                      )
                : renderFromPunishments(
                      "lists.navigation.previous-inactive",
                      Map.of()
                  );

        Component current = renderFromPunishments(
            "lists.navigation.current",
            Map.of(
                "page",
                Integer.toString(page),
                "pages",
                Integer.toString(totalPages)
            )
        );

        Component next =
            page < totalPages
                ? renderFromPunishments(
                      "lists.navigation.next-active",
                      Map.of()
                  )
                      .clickEvent(
                          ClickEvent.runCommand(commandBase + (page + 1))
                      )
                      .hoverEvent(
                          HoverEvent.showText(
                              renderFromPunishments(
                                  "lists.navigation.next-hover",
                                  Map.of("page", Integer.toString(page + 1))
                              )
                          )
                      )
                : renderFromPunishments(
                      "lists.navigation.next-inactive",
                      Map.of()
                  );

        return Component.empty()
            .append(previous)
            .append(Component.space())
            .append(current)
            .append(Component.space())
            .append(next);
    }

    private Component renderPunishmentListSearchNavigation(
        PunishmentType type,
        String query,
        int page,
        int totalPages
    ) {
        String commandBase = switch (type) {
            case BAN -> "/banlistsearch ";
            case WARN -> "/warnlistsearch ";
            default -> null;
        };

        if (commandBase == null || totalPages <= 1) {
            return Component.empty();
        }

        Component previous =
            page > 1
                ? renderFromPunishments(
                      "lists.navigation.previous-active",
                      Map.of()
                  )
                      .clickEvent(
                          ClickEvent.runCommand(commandBase + query + " " + (page - 1))
                      )
                      .hoverEvent(
                          HoverEvent.showText(
                              renderFromPunishments(
                                  "lists.navigation.previous-hover",
                                  Map.of("page", Integer.toString(page - 1))
                              )
                          )
                      )
                : renderFromPunishments(
                      "lists.navigation.previous-inactive",
                      Map.of()
                  );

        Component current = renderFromPunishments(
            "lists.navigation.current",
            Map.of(
                "page",
                Integer.toString(page),
                "pages",
                Integer.toString(totalPages)
            )
        );

        Component next =
            page < totalPages
                ? renderFromPunishments(
                      "lists.navigation.next-active",
                      Map.of()
                  )
                      .clickEvent(
                          ClickEvent.runCommand(commandBase + query + " " + (page + 1))
                      )
                      .hoverEvent(
                          HoverEvent.showText(
                              renderFromPunishments(
                                  "lists.navigation.next-hover",
                                  Map.of("page", Integer.toString(page + 1))
                              )
                          )
                      )
                : renderFromPunishments(
                      "lists.navigation.next-inactive",
                      Map.of()
                  );

        return Component.empty()
            .append(previous)
            .append(Component.space())
            .append(current)
            .append(Component.space())
            .append(next);
    }

    private void refreshPlayerState(UUID targetUuid) throws Exception {
        long now = System.currentTimeMillis();
        boolean banned = this.punishmentRepository
            .findActiveByTypeAndTarget(PunishmentType.BAN, targetUuid, now)
            .isPresent();
        PunishmentRecord mute = this.punishmentRepository
            .findActiveByTypeAndTarget(PunishmentType.MUTE, targetUuid, now)
            .orElse(null);
        int activeWarns = this.punishmentRepository.countActiveForTarget(
            PunishmentType.WARN,
            targetUuid,
            now
        );
        this.playerProfileRepository.updateBanState(
            targetUuid,
            banned,
            banned
                ? this.punishmentRepository
                      .findActiveByTypeAndTarget(
                          PunishmentType.BAN,
                          targetUuid,
                          now
                      )
                      .map(PunishmentRecord::expiresAt)
                      .orElse(null)
                : null
        );
        this.playerProfileRepository.updateMuteState(
            targetUuid,
            mute != null,
            mute == null ? null : mute.expiresAt()
        );
        this.playerProfileRepository.updateWarnCount(targetUuid, activeWarns);
        this.muteCache.put(targetUuid, new CachedMuteState(mute, now));
    }

    private boolean isSelfTarget(CommandSender sender, UUID targetUuid) {
        return (
            sender instanceof Player player &&
            player.getUniqueId().equals(targetUuid)
        );
    }

    private Component renderBanLikeScreen(
        String path,
        PunishmentRecord record
    ) {
        return renderFromPunishments(
            path,
            Map.of(
                "id",
                Long.toString(record.id()),
                "moderator",
                record.moderatorName(),
                "reason",
                record.reason(),
                "expires",
                formatAppliedDuration(record)
            )
        );
    }

    private Component renderFromPunishments(
        String path,
        Map<String, String> placeholders
    ) {
        return this.messageFormatter.render(
            "punishments." + path,
            placeholders
        );
    }

    private Component renderFromMessages(
        String path,
        Map<String, String> placeholders
    ) {
        return this.messageFormatter.render(path, placeholders);
    }

    private FileConfiguration config() {
        return this.context
            .configManager()
            .require("punishments")
            .configuration();
    }

    private String formatExpires(Long expiresAt) {
        if (expiresAt == null) {
            return this.messageFormatter.getOrDefault(
                "punishments.defaults.permanent",
                "Навсегда"
            );
        }

        String pattern = this.messageFormatter.getOrDefault(
            "punishments.formats.date-format",
            "dd.MM.yyyy HH:mm:ss"
        );
        return DateTimeFormatter.ofPattern(pattern)
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(expiresAt));
    }

    private String formatDetailsTime(PunishmentRecord record) {
        if (record.type() == PunishmentType.KICK) {
            return "";
        }

        return formatRemainingDuration(record.expiresAt());
    }

    private String formatAppliedDuration(PunishmentRecord record) {
        if (record.type() == PunishmentType.KICK) {
            return this.messageFormatter.getOrDefault(
                "punishments.defaults.instant",
                "Моментально"
            );
        }

        return formatOriginalDuration(record.createdAt(), record.expiresAt());
    }

    private String formatOriginalDuration(Long createdAt, Long expiresAt) {
        if (expiresAt == null) {
            return this.messageFormatter.getOrDefault(
                "punishments.defaults.permanent",
                "Навсегда"
            );
        }

        long durationMillis = Math.max(0L, expiresAt - createdAt);
        return formatDurationMillis(durationMillis);
    }

    private String formatRemainingDuration(Long expiresAt) {
        if (expiresAt == null) {
            return this.messageFormatter.getOrDefault(
                "punishments.defaults.permanent",
                "Навсегда"
            );
        }

        long remainingMillis = Math.max(
            0L,
            expiresAt - System.currentTimeMillis()
        );
        return formatDurationMillis(remainingMillis, true);
    }

    private String formatDurationMillis(long durationMillis) {
        return formatDurationMillis(durationMillis, false);
    }

    private String formatDurationMillis(
        long durationMillis,
        boolean dynamicDisplay
    ) {
        long totalSeconds = Math.max(1L, (durationMillis + 500L) / 1_000L);
        long days = totalSeconds / 86_400L;
        long hours = (totalSeconds % 86_400L) / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;

        StringBuilder builder = new StringBuilder();

        if (days > 0L) {
            builder.append(days).append("д");
            if (hours > 0L) {
                builder.append(", ").append(hours).append("ч");
            }
            if (minutes > 0L) {
                builder.append(", ").append(minutes).append("м");
            }
            return builder.toString();
        }

        if (hours > 0L) {
            builder.append(hours).append("ч");
            if (minutes > 0L) {
                builder.append(", ").append(minutes).append("м");
            }
            return builder.toString();
        }

        if (minutes > 0L) {
            builder.append(minutes).append("м");
            if (dynamicDisplay || minutes < 10L || seconds > 0L) {
                builder.append(", ").append(seconds).append("с");
            }
            return builder.toString();
        }

        return seconds + "с";
    }

    private Component renderPunishmentDetails(PunishmentRecord record) {
        String detailsKey =
            record.type() == PunishmentType.KICK
                ? "broadcasts.details-kick"
                : "broadcasts.details";

        return renderFromPunishments(
            detailsKey,
            Map.of(
                "id",
                Long.toString(record.id()),
                "moderator",
                record.moderatorName(),
                "reason",
                record.reason(),
                "expires",
                formatDetailsTime(record)
            )
        );
    }

    private int pageSize() {
        return config().getInt("lists.page-size", 6);
    }

    private String typeDisplay(PunishmentType type) {
        return this.messageFormatter.getOrDefault(
            "punishments.formats.type-names." + type.key(),
            type.name().toLowerCase()
        );
    }

    private String typeDisplayPlural(PunishmentType type) {
        return this.messageFormatter.getOrDefault(
            "punishments.formats.type-names-plural." + type.key(),
            type.name().toLowerCase()
        );
    }

    private record CachedMuteState(PunishmentRecord record, long checkedAt) {}
}
