package dev.toxi.aurionGo.feature.punishment;

import dev.toxi.aurionGo.shared.AurionContext;
import dev.toxi.aurionGo.storage.player.PlayerProfileRecord;
import dev.toxi.aurionGo.storage.player.PlayerProfileRepository;
import dev.toxi.aurionGo.storage.punishment.PunishmentCreateRequest;
import dev.toxi.aurionGo.storage.punishment.PunishmentPage;
import dev.toxi.aurionGo.storage.punishment.PunishmentRecord;
import dev.toxi.aurionGo.storage.punishment.PunishmentRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PunishmentService {
    private static final long MUTE_CACHE_TTL_MILLIS = 5_000L;
    private final AurionContext context;
    private final PunishmentRepository punishmentRepository;
    private final PlayerProfileRepository playerProfileRepository;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final ConcurrentMap<UUID, CachedMuteState> muteCache = new ConcurrentHashMap<>();

    public PunishmentService(AurionContext context, PunishmentRepository punishmentRepository, PlayerProfileRepository playerProfileRepository) {
        this.context = context;
        this.punishmentRepository = punishmentRepository;
        this.playerProfileRepository = playerProfileRepository;
    }

    public void applyBan(CommandSender sender, String targetInput, String reason, Long durationMillis) {
        applyPersistentPunishment(sender, PunishmentType.BAN, targetInput, reason, durationMillis, false);
    }

    public void applyMute(CommandSender sender, String targetInput, String reason, Long durationMillis) {
        applyPersistentPunishment(sender, PunishmentType.MUTE, targetInput, reason, durationMillis, true);
    }

    public void applyWarn(CommandSender sender, String targetInput, String reason, Long durationMillis) {
        applyPersistentPunishment(sender, PunishmentType.WARN, targetInput, reason, durationMillis, false);
    }

    public void applyKick(CommandSender sender, String targetInput, String reason) {
        PunishmentTarget target = resolveTarget(targetInput);

        if (target == null) {
            sender.sendMessage(renderFromPunishments("messages.target-not-found", Map.of("target", targetInput)));
            return;
        }

        if (target.onlinePlayer() == null) {
            sender.sendMessage(renderFromPunishments("messages.target-must-be-online", Map.of("target", target.nickname())));
            return;
        }

        if (isSelfTarget(sender, target.uuid())) {
            sender.sendMessage(renderFromPunishments("messages.cannot-target-self", Map.of()));
            return;
        }

        try {
            long now = System.currentTimeMillis();
            PunishmentRecord record = createRecord(sender, PunishmentType.KICK, target, reason, null, false, now);
            broadcastPunishment(record);

            if (target.onlinePlayer() != null) {
                target.onlinePlayer().kick(renderBanLikeScreen("screens.kick-screen", record));
            }
        } catch (Exception exception) {
            sender.sendMessage(renderFromPunishments("messages.lookup-error", Map.of("error", exception.getMessage())));
        }
    }

    public void listPunishments(CommandSender sender, PunishmentType type, int page) {
        try {
            PunishmentPage punishmentPage = this.punishmentRepository.listActive(type, page, pageSize(), System.currentTimeMillis());

            if (punishmentPage.entries().isEmpty()) {
                sender.sendMessage(renderFromPunishments("lists.empty", Map.of("type", typeDisplayPlural(type))));
                return;
            }

            sender.sendMessage(renderFromPunishments(
                    "lists.header",
                    Map.of(
                            "type", typeDisplayPlural(type),
                            "page", Integer.toString(punishmentPage.page()),
                            "pages", Integer.toString(punishmentPage.totalPages()),
                            "count", Integer.toString(punishmentPage.totalEntries())
                    )
            ));

            for (PunishmentRecord record : punishmentPage.entries()) {
                sender.sendMessage(renderFromPunishments(
                        "lists.entry",
                        Map.of(
                                "id", Long.toString(record.id()),
                                "target", record.targetNickname(),
                                "moderator", record.moderatorName(),
                                "reason", record.reason(),
                                "expires", formatExpires(record.expiresAt())
                        )
                ));
            }
        } catch (Exception exception) {
            sender.sendMessage(renderFromPunishments("messages.lookup-error", Map.of("error", exception.getMessage())));
        }
    }

    public void removePunishment(CommandSender sender, PunishmentType type, String query) {
        long now = System.currentTimeMillis();
        UUID moderatorUuid = sender instanceof Player player ? player.getUniqueId() : null;
        String moderatorName = sender.getName();

        try {
            boolean success;
            UUID affectedTarget = null;

            if (query.matches("\\d+")) {
                Optional<PunishmentRecord> record = this.punishmentRepository.findById(Long.parseLong(query));
                affectedTarget = record.map(PunishmentRecord::targetUuid).orElse(null);
                success = this.punishmentRepository.deactivateById(type, Long.parseLong(query), moderatorUuid, moderatorName, "Снято вручную", now);
            } else {
                PunishmentTarget target = resolveTarget(query);

                if (target == null) {
                    sender.sendMessage(renderFromPunishments("messages.target-not-found", Map.of("target", query)));
                    return;
                }

                affectedTarget = target.uuid();
                success = this.punishmentRepository.deactivateLatestByTarget(type, target.uuid(), moderatorUuid, moderatorName, "Снято вручную", now, now);
            }

            if (!success) {
                sender.sendMessage(renderFromPunishments("messages.punishment-not-found", Map.of("type", typeDisplay(type))));
                return;
            }

            if (affectedTarget != null) {
                refreshPlayerState(affectedTarget);
            }

            sender.sendMessage(renderFromPunishments("messages.removed", Map.of("type", typeDisplay(type), "query", query)));
        } catch (Exception exception) {
            sender.sendMessage(renderFromPunishments("messages.lookup-error", Map.of("error", exception.getMessage())));
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
                            "id", Long.toString(activeBan.id()),
                            "moderator", activeBan.moderatorName(),
                            "reason", activeBan.reason(),
                            "expires", formatExpires(activeBan.expiresAt())
                    )
            );
        } catch (Exception exception) {
            this.context.plugin().getLogger().warning("Не удалось проверить бан при входе: " + exception.getMessage());
            return null;
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
                            "id", Long.toString(activeMute.id()),
                            "reason", activeMute.reason(),
                            "expires", formatExpires(activeMute.expiresAt())
                    )
            );
        } catch (Exception exception) {
            this.context.plugin().getLogger().warning("Не удалось проверить мут игрока: " + exception.getMessage());
            return null;
        }
    }

    public void sendMuteBlockMessage(Player player, Component component) {
        this.context.plugin().getServer().getScheduler().runTask(this.context.plugin(), task -> player.sendActionBar(component));
    }

    public boolean hasActiveMute(UUID targetUuid) {
        try {
            return findActiveMute(targetUuid) != null;
        } catch (Exception exception) {
            this.context.plugin().getLogger().warning("Не удалось проверить мут игрока: " + exception.getMessage());
            return false;
        }
    }

    public boolean shouldBlockMutedCommand(UUID targetUuid, String rawCommand) {
        if (!hasActiveMute(targetUuid)) {
            return false;
        }

        String normalized = rawCommand.startsWith("/") ? rawCommand.substring(1) : rawCommand;
        String label = normalized.split("\\s+", 2)[0].toLowerCase();
        List<String> blockedCommands = config().getStringList("mutes.blocked-commands");
        return blockedCommands.stream().map(String::toLowerCase).anyMatch(label::equals);
    }

    public Component renderNoPermission() {
        return renderFromMessages("errors.no-permission", Map.of());
    }

    public Component renderUsage(PunishmentType type) {
        return renderFromPunishments("commands." + type.key() + ".usage", Map.of());
    }

    public Component renderRemoveUsage(PunishmentType type) {
        return renderFromPunishments("commands.un" + type.key() + ".usage", Map.of());
    }

    public List<String> suggestActiveTargets(PunishmentType type, String prefix) {
        try {
            return this.punishmentRepository.findActiveTargetNames(type, prefix, 15, System.currentTimeMillis());
        } catch (Exception exception) {
            return List.of();
        }
    }

    private void applyPersistentPunishment(CommandSender sender, PunishmentType type, String targetInput, String reason, Long durationMillis, boolean requireReason) {
        PunishmentTarget target = resolveTarget(targetInput);

        if (target == null) {
            sender.sendMessage(renderFromPunishments("messages.target-not-found", Map.of("target", targetInput)));
            return;
        }

        if (isSelfTarget(sender, target.uuid())) {
            sender.sendMessage(renderFromPunishments("messages.cannot-target-self", Map.of()));
            return;
        }

        if (requireReason && (reason == null || reason.isBlank())) {
            sender.sendMessage(renderFromPunishments("messages.reason-required", Map.of("type", typeDisplay(type))));
            return;
        }

        try {
            long now = System.currentTimeMillis();
            PunishmentRecord existing = activePunishment(type, target.uuid());

            if (existing != null && type != PunishmentType.WARN) {
                sender.sendMessage(renderFromPunishments("messages.already-active", Map.of("type", typeDisplay(type), "target", target.nickname())));
                return;
            }

            Long expiresAt = durationMillis == null ? null : now + durationMillis;
            PunishmentRecord record = createRecord(sender, type, target, reason, expiresAt, type != PunishmentType.KICK, now);
            refreshPlayerState(target.uuid());
            broadcastPunishment(record);

            if (type == PunishmentType.BAN && target.onlinePlayer() != null) {
                target.onlinePlayer().kick(renderBanLikeScreen("screens.ban", record));
            }
        } catch (Exception exception) {
            sender.sendMessage(renderFromPunishments("messages.lookup-error", Map.of("error", exception.getMessage())));
        }
    }

    private PunishmentRecord createRecord(CommandSender sender, PunishmentType type, PunishmentTarget target, String reason, Long expiresAt, boolean active, long now) throws Exception {
        UUID moderatorUuid = sender instanceof Player player ? player.getUniqueId() : null;
        String moderatorName = sender instanceof ConsoleCommandSender ? "CONSOLE" : sender.getName();
        String finalReason = (reason == null || reason.isBlank())
                ? config().getString("defaults.reason", "Не указана")
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
        if (!config().getBoolean("broadcasts." + record.type().key() + ".enabled", true)) {
            return;
        }

        Component details = renderFromPunishments(
                "broadcasts.details",
                Map.of(
                        "id", Long.toString(record.id()),
                        "moderator", record.moderatorName(),
                        "reason", record.reason(),
                        "expires", formatDetailsTime(record)
                )
        );

        Component more = renderFromPunishments("broadcasts.more", Map.of())
                .hoverEvent(HoverEvent.showText(details));

        Component base = renderFromPunishments(
                "broadcasts." + record.type().key() + ".format",
                Map.of(
                        "moderator", record.moderatorName(),
                        "target", record.targetNickname()
                )
        );

        Bukkit.getServer().broadcast(Component.empty().append(base).append(Component.space()).append(more));
    }

    private PunishmentTarget resolveTarget(String input) {
        Player onlinePlayer = Bukkit.getPlayerExact(input);

        if (onlinePlayer != null) {
            return new PunishmentTarget(onlinePlayer.getUniqueId(), onlinePlayer.getName(), onlinePlayer);
        }

        try {
            Optional<PlayerProfileRecord> profile = this.playerProfileRepository.findByNickname(input);

            if (profile.isEmpty()) {
                return null;
            }

            return new PunishmentTarget(profile.get().uuid(), profile.get().nickname(), null);
        } catch (Exception exception) {
            throw new IllegalStateException("Не удалось найти игрока " + input, exception);
        }
    }

    private PunishmentRecord activePunishment(PunishmentType type, UUID targetUuid) throws Exception {
        long now = System.currentTimeMillis();
        int expired = this.punishmentRepository.deactivateExpired(type, targetUuid, now);

        if (expired > 0) {
            refreshPlayerState(targetUuid);
        }

        return this.punishmentRepository.findActiveByTypeAndTarget(type, targetUuid, now).orElse(null);
    }

    private PunishmentRecord findActiveMute(UUID targetUuid) throws Exception {
        long now = System.currentTimeMillis();
        CachedMuteState cached = this.muteCache.get(targetUuid);

        if (cached != null) {
            if (cached.record() != null && (cached.record().expiresAt() == null || cached.record().expiresAt() > now)) {
                return cached.record();
            }

            if (cached.record() == null && now - cached.checkedAt() < MUTE_CACHE_TTL_MILLIS) {
                return null;
            }
        }

        PunishmentRecord activeMute = activePunishment(PunishmentType.MUTE, targetUuid);
        this.muteCache.put(targetUuid, new CachedMuteState(activeMute, now));
        return activeMute;
    }

    private void refreshPlayerState(UUID targetUuid) throws Exception {
        long now = System.currentTimeMillis();
        boolean banned = this.punishmentRepository.findActiveByTypeAndTarget(PunishmentType.BAN, targetUuid, now).isPresent();
        PunishmentRecord mute = this.punishmentRepository.findActiveByTypeAndTarget(PunishmentType.MUTE, targetUuid, now).orElse(null);
        int activeWarns = this.punishmentRepository.countActiveForTarget(PunishmentType.WARN, targetUuid, now);
        this.playerProfileRepository.updateBanState(targetUuid, banned, banned ? this.punishmentRepository.findActiveByTypeAndTarget(PunishmentType.BAN, targetUuid, now).map(PunishmentRecord::expiresAt).orElse(null) : null);
        this.playerProfileRepository.updateMuteState(targetUuid, mute != null, mute == null ? null : mute.expiresAt());
        this.playerProfileRepository.updateWarnCount(targetUuid, activeWarns);
        this.muteCache.put(targetUuid, new CachedMuteState(mute, now));
    }

    private boolean isSelfTarget(CommandSender sender, UUID targetUuid) {
        return sender instanceof Player player && player.getUniqueId().equals(targetUuid);
    }

    private Component renderBanLikeScreen(String path, PunishmentRecord record) {
        return renderFromPunishments(
                path,
                Map.of(
                        "id", Long.toString(record.id()),
                        "moderator", record.moderatorName(),
                        "reason", record.reason(),
                        "expires", formatDetailsTime(record)
                )
        );
    }

    private Component renderFromPunishments(String path, Map<String, String> placeholders) {
        String template = config().getString(path, "<color:#FF4F4F>Отсутствует шаблон: " + path);
        String resolved = template;

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolved = resolved.replace("{" + entry.getKey() + "}", this.miniMessage.escapeTags(entry.getValue()));
        }

        return this.miniMessage.deserialize(resolved);
    }

    private Component renderFromMessages(String path, Map<String, String> placeholders) {
        String template = this.context.configManager().require("messages").configuration().getString(path, "<color:#FF4F4F>Отсутствует шаблон: " + path);
        String resolved = template;

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolved = resolved.replace("{" + entry.getKey() + "}", this.miniMessage.escapeTags(entry.getValue()));
        }

        return this.miniMessage.deserialize(resolved);
    }

    private FileConfiguration config() {
        return this.context.configManager().require("punishments").configuration();
    }

    private String formatExpires(Long expiresAt) {
        if (expiresAt == null) {
            return config().getString("defaults.permanent", "Навсегда");
        }

        String pattern = config().getString("formats.date-format", "dd.MM.yyyy HH:mm:ss");
        return DateTimeFormatter.ofPattern(pattern)
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(expiresAt));
    }

    private String formatDetailsTime(PunishmentRecord record) {
        if (record.type() == PunishmentType.KICK) {
            return config().getString("defaults.instant", "Моментально");
        }

        return formatExpires(record.expiresAt());
    }

    private int pageSize() {
        return config().getInt("lists.page-size", 10);
    }

    private String typeDisplay(PunishmentType type) {
        return config().getString("formats.type-names." + type.key(), type.name().toLowerCase());
    }

    private String typeDisplayPlural(PunishmentType type) {
        return config().getString("formats.type-names-plural." + type.key(), type.name().toLowerCase());
    }

    private record CachedMuteState(PunishmentRecord record, long checkedAt) {
    }
}
