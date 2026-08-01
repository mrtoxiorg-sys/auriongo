package dev.toxi.aurionGo.feature.player;

import dev.toxi.aurionGo.feature.punishment.PunishmentType;
import dev.toxi.aurionGo.message.MessageFormatter;
import dev.toxi.aurionGo.shared.AurionContext;
import dev.toxi.aurionGo.storage.player.PlayerProfileRecord;
import dev.toxi.aurionGo.storage.player.PlayerProfileRepository;
import dev.toxi.aurionGo.storage.player.PlayerProfileSnapshot;
import dev.toxi.aurionGo.storage.punishment.PunishmentRepository;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.nametag.NameTagManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class PlayerProfileService {

    static final String HIDDEN_HEADER_TAG = "au. ";

    private static final String HIDDEN_NAMETAG_TEAM = "aurion-hidden-nt";
    private static final String NAMETAG_BYPASS_PERMISSION =
        "auriongo.bypass.nametag";

    private static final long WORLD_SWITCH_SUPPRESSION_WINDOW_MILLIS = 15_000L;

    private final AurionContext context;
    private final PlayerProfileRepository repository;
    private final PunishmentRepository punishmentRepository;
    private final MessageFormatter messageFormatter;
    private final ConcurrentMap<UUID, HideSettings> hideSettings =
        new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> temporaryJoinQuitSuppressions =
        new ConcurrentHashMap<>();

    public PlayerProfileService(
        AurionContext context,
        PlayerProfileRepository repository
    ) {
        this.context = context;
        this.repository = repository;
        this.punishmentRepository = new PunishmentRepository(
            context.serviceRegistry().require(dev.toxi.aurionGo.storage.DatabaseManager.class)
        );
        this.messageFormatter = context
            .serviceRegistry()
            .require(MessageFormatter.class);
    }

    public void trackJoin(Player player) {
        long now = System.currentTimeMillis();
        String ipAddress = resolveIpAddress(player);
        PlayerProfileSnapshot snapshot = new PlayerProfileSnapshot(
            player.getUniqueId(),
            player.getName(),
            ipAddress,
            now,
            now
        );

        this.context
            .plugin()
            .getServer()
            .getScheduler()
            .runTaskAsynchronously(this.context.plugin(), task -> {
                try {
                    boolean firstJoin = this.repository.saveOrUpdateJoin(
                        snapshot
                    );
                    if (!"unknown".equals(snapshot.ipAddress())) {
                        this.repository.saveOrUpdateIpHistory(snapshot);
                    }
                    HideSettings settings = loadHideSettings(player);
                    this.hideSettings.put(player.getUniqueId(), settings);
                        this.context
                            .plugin()
                            .getServer()
                            .getScheduler()
                            .runTask(this.context.plugin(), syncTask -> {
                                applyNametagState(player);
                                applyNametagStateForViewer(player);
                            });

                    if (firstJoin) {
                        this.context
                            .plugin()
                            .getServer()
                            .getScheduler()
                            .runTask(this.context.plugin(), syncTask ->
                                this.context
                                    .plugin()
                                    .getServer()
                                    .broadcast(
                                        render(
                                            "player-data.first-join",
                                            Map.of("player", player.getName())
                                        )
                                    )
                            );
                    }
                } catch (Exception exception) {
                    this.context
                        .plugin()
                        .getLogger()
                        .warning(
                            "Не удалось сохранить профиль игрока " +
                                player.getName() +
                                ": " +
                                exception.getMessage()
                        );
                }
            });
    }

    public void trackQuit(Player player) {
        this.hideSettings.remove(player.getUniqueId());
        this.temporaryJoinQuitSuppressions.remove(player.getUniqueId());
    }

    public HideSettings getHideSettings(Player player) {
        HideSettings cached = this.hideSettings.get(player.getUniqueId());

        if (cached != null) {
            return cached;
        }

        HideSettings loaded = loadHideSettings(player);
        this.hideSettings.put(player.getUniqueId(), loaded);
        return loaded;
    }

    public boolean hidesJoinLeaveMessages(Player player) {
        return getHideSettings(player).hideJoinLeaveMessages();
    }

    public boolean shouldSuppressJoinMessage(Player player) {
        Long suppressUntil = consumeSuppressJoinQuitUntil(player);
        return suppressUntil != null && suppressUntil > System.currentTimeMillis();
    }

    public boolean shouldSuppressQuitMessage(Player player) {
        Long suppressUntil = this.temporaryJoinQuitSuppressions.get(
            player.getUniqueId()
        );

        if (suppressUntil == null) {
            return false;
        }

        if (suppressUntil <= System.currentTimeMillis()) {
            this.temporaryJoinQuitSuppressions.remove(player.getUniqueId());
            return false;
        }

        this.temporaryJoinQuitSuppressions.remove(player.getUniqueId());
        return true;
    }

    public void suppressNextJoinQuitForWorldSwitch(Player player) {
        long suppressUntil =
            System.currentTimeMillis() + WORLD_SWITCH_SUPPRESSION_WINDOW_MILLIS;
        this.temporaryJoinQuitSuppressions.put(player.getUniqueId(), suppressUntil);

        this.context
            .plugin()
            .getServer()
            .getScheduler()
            .runTaskAsynchronously(this.context.plugin(), task -> {
                try {
                    this.repository.updateSuppressJoinQuitUntil(
                            player.getUniqueId(),
                            suppressUntil
                        );
                } catch (Exception exception) {
                    this.context
                        .plugin()
                        .getLogger()
                        .warning(
                            "Не удалось сохранить suppress-флаг world-перехода для игрока " +
                            player.getName() +
                            ": " +
                            exception.getMessage()
                        );
                }
            });
    }

    public boolean hidesAfkMessages(Player player) {
        return getHideSettings(player).hideAfkMessages();
    }

    public boolean toggleJoinLeaveMessages(Player player) {
        HideSettings updated = getHideSettings(
            player
        ).withHideJoinLeaveMessages(!hidesJoinLeaveMessages(player));
        this.hideSettings.put(player.getUniqueId(), updated);
        persistHideSettings(player, updated);
        return updated.hideJoinLeaveMessages();
    }

    public boolean toggleAfkMessages(Player player) {
        HideSettings updated = getHideSettings(player).withHideAfkMessages(
            !hidesAfkMessages(player)
        );
        this.hideSettings.put(player.getUniqueId(), updated);
        persistHideSettings(player, updated);
        return updated.hideAfkMessages();
    }

    public boolean hidesNametag(Player player) {
        return getHideSettings(player).hideNametag();
    }

    public boolean toggleNametag(Player player) {
        HideSettings updated = getHideSettings(player).withHideNametag(
            !hidesNametag(player)
        );
        this.hideSettings.put(player.getUniqueId(), updated);
        applyNametagState(player);
        persistHideSettings(player, updated);
        return updated.hideNametag();
    }

    public boolean isSpyEnabled(Player player) {
        return getHideSettings(player).spyEnabled();
    }

    public boolean toggleSpy(Player player) {
        HideSettings updated = getHideSettings(player).withSpyEnabled(
            !isSpyEnabled(player)
        );
        this.hideSettings.put(player.getUniqueId(), updated);
        persistHideSettings(player, updated);
        return updated.spyEnabled();
    }

    public void sendPlayerInfo(CommandSender sender, String nickname) {
        this.context
            .plugin()
            .getServer()
            .getScheduler()
            .runTaskAsynchronously(this.context.plugin(), task -> {
                try {
                    Optional<PlayerProfileRecord> optionalProfile =
                        this.repository.findByNickname(nickname);
                    this.context
                        .plugin()
                        .getServer()
                        .getScheduler()
                        .runTask(this.context.plugin(), syncTask -> {
                            if (optionalProfile.isEmpty()) {
                                sender.sendMessage(
                                    render(
                                        "player-data.not-found",
                                        Map.of("player", nickname)
                                    )
                                );
                                return;
                            }

                            PlayerProfileRecord profile = optionalProfile.get();
                            long now = System.currentTimeMillis();
                            sender.sendMessage(
                                render(
                                    "player-data.header",
                                    Map.of("player", profile.nickname())
                                )
                            );
                            sender.sendMessage(
                                renderCopyable(
                                    "player-data.nickname",
                                    Map.of("value", profile.nickname()),
                                    profile.nickname()
                                )
                            );
                            sender.sendMessage(
                                renderCopyable(
                                    "player-data.uuid",
                                    Map.of("value", profile.uuid().toString()),
                                    profile.uuid().toString()
                                )
                            );
                            sender.sendMessage(
                                render(
                                    "player-data.first-join-line",
                                    Map.of(
                                        "value",
                                        formatTimestamp(profile.firstJoin())
                                    )
                                )
                            );
                            sender.sendMessage(
                                render(
                                    "player-data.last-join-line",
                                    Map.of(
                                        "value",
                                        formatTimestamp(profile.lastJoin())
                                    )
                                )
                            );
                            sender.sendMessage(
                                renderCopyable(
                                    "player-data.ip-line",
                                    Map.of("value", profile.ipAddress()),
                                    profile.ipAddress()
                                )
                            );

                            sendPunishmentStats(sender, profile.uuid(), now);
                        });
                } catch (Exception exception) {
                    this.context
                        .plugin()
                        .getServer()
                        .getScheduler()
                        .runTask(this.context.plugin(), syncTask ->
                            sender.sendMessage(
                                render(
                                    "player-data.lookup-error",
                                    Map.of("error", exception.getMessage())
                                )
                            )
                        );
                }
            });
    }

    private void sendPunishmentStats(CommandSender sender, UUID targetUuid, long now) {
        try {
            int activeWarns = this.punishmentRepository.countActiveForTarget(
                PunishmentType.WARN, targetUuid, now
            );
            int activeBans = this.punishmentRepository.countActiveForTarget(
                PunishmentType.BAN, targetUuid, now
            );
            int totalWarns = this.punishmentRepository.countTotalForTarget(
                PunishmentType.WARN, targetUuid
            );
            int totalBans = this.punishmentRepository.countTotalForTarget(
                PunishmentType.BAN, targetUuid
            );
            int totalMutes = this.punishmentRepository.countTotalForTarget(
                PunishmentType.MUTE, targetUuid
            );
            int totalKicks = this.punishmentRepository.countTotalForTarget(
                PunishmentType.KICK, targetUuid
            );

            sender.sendMessage(
                render(
                    "player-data.punishment-stats-header",
                    Map.of()
                )
            );
            sender.sendMessage(
                render(
                    "player-data.punishment-stat",
                    Map.of(
                        "type", "варнов",
                        "active", String.valueOf(activeWarns),
                        "total", String.valueOf(totalWarns)
                    )
                )
            );
            sender.sendMessage(
                render(
                    "player-data.punishment-stat",
                    Map.of(
                        "type", "банов",
                        "active", String.valueOf(activeBans),
                        "total", String.valueOf(totalBans)
                    )
                )
            );
            sender.sendMessage(
                render(
                    "player-data.punishment-stat-past",
                    Map.of(
                        "type", "мутов",
                        "total", String.valueOf(totalMutes)
                    )
                )
            );
            sender.sendMessage(
                render(
                    "player-data.punishment-stat-past",
                    Map.of(
                        "type", "киков",
                        "total", String.valueOf(totalKicks)
                    )
                )
            );
        } catch (Exception exception) {
            this.context
                .plugin()
                .getLogger()
                .warning(
                    "Не удалось получить статистику наказаний: " +
                        exception.getMessage()
                );
        }
    }

    public void sendIpMatches(CommandSender sender, String ipAddress) {
        this.context
            .plugin()
            .getServer()
            .getScheduler()
            .runTaskAsynchronously(this.context.plugin(), task -> {
                try {
                    List<PlayerProfileRecord> profiles = this.repository.findByIpAddress(
                        ipAddress
                    );
                    this.context
                        .plugin()
                        .getServer()
                        .getScheduler()
                        .runTask(this.context.plugin(), syncTask -> {
                            if (profiles.isEmpty()) {
                                sender.sendMessage(
                                    render(
                                        "player-data.ip-not-found",
                                        Map.of("ip", ipAddress)
                                    )
                                );
                                return;
                            }

                            sender.sendMessage(
                                render(
                                    "player-data.ip-header",
                                    Map.of(
                                        "ip", ipAddress,
                                        "count", String.valueOf(profiles.size())
                                    )
                                )
                            );

                            for (PlayerProfileRecord profile : profiles) {
                                Component nameLine = renderCopyable(
                                    "player-data.ip-entry-name",
                                    Map.of("player", profile.nickname()),
                                    profile.nickname()
                                );
                                Component uuidLine = renderCopyable(
                                    "player-data.ip-entry-uuid",
                                    Map.of("uuid", profile.uuid().toString()),
                                    profile.uuid().toString()
                                );
                                Component lastJoinLine = render(
                                    "player-data.ip-entry-lastjoin",
                                    Map.of("lastJoin", formatTimestamp(profile.lastJoin()))
                                );
                                sender.sendMessage(
                                    Component.empty()
                                        .append(nameLine)
                                        .append(Component.space())
                                        .append(uuidLine)
                                        .append(Component.space())
                                        .append(lastJoinLine)
                                );
                            }
                        });
                } catch (Exception exception) {
                    this.context
                        .plugin()
                        .getServer()
                        .getScheduler()
                        .runTask(this.context.plugin(), syncTask ->
                            sender.sendMessage(
                                render(
                                    "player-data.ip-lookup-error",
                                    Map.of("error", exception.getMessage())
                                )
                            )
                        );
                }
            });
    }

    public void sendNicknameActionBar(Player viewer, Player target) {
        viewer.sendActionBar(
            render(
                "player-data.reveal-nickname",
                Map.of("player", target.getName())
            )
        );
    }

    public void sendUsage(CommandSender sender) {
        sender.sendMessage(render("player-data.usage", Map.of()));
    }

    public void sendIpUsage(CommandSender sender) {
        sender.sendMessage(render("player-data.ip-usage", Map.of()));
    }

    public void sendNoPermission(CommandSender sender) {
        sender.sendMessage(render("errors.no-permission", Map.of()));
    }

    public Component renderPlayerOnly() {
        return render("errors.player-only", Map.of());
    }

    public void sendHideUsage(CommandSender sender, String label) {
        sender.sendMessage(
            render(
                "player-data.hide-usage",
                Map.of("command", label.toLowerCase())
            )
        );
    }

    public void sendHideToggleResult(
        CommandSender sender,
        String type,
        boolean enabled
    ) {
        sender.sendMessage(
            render(
                enabled
                    ? "player-data.hide-enabled"
                    : "player-data.hide-disabled",
                Map.of("type", type)
            )
        );
    }

    public Component renderToggleNametagUsage(String label) {
        return Component.text("Использование: /" + label.toLowerCase());
    }

    public Component renderToggleNametagResult(boolean hidden) {
        return Component.text(
            hidden
                ? "Nametag над головой скрыт."
                : "Nametag над головой снова отображается."
        );
    }

    private HideSettings loadHideSettings(Player player) {
        try {
            return this.repository
                .findByNickname(player.getName())
                .map(profile ->
                    new HideSettings(
                        profile.hideJoinLeaveMessages(),
                        profile.hideAfkMessages(),
                        profile.spyEnabled(),
                        profile.hideNametag()
                    )
                )
                .orElse(HideSettings.DEFAULT);
        } catch (Exception exception) {
            this.context
                .plugin()
                .getLogger()
                .warning(
                    "Не удалось загрузить hide-настройки игрока " +
                        player.getName() +
                        ": " +
                        exception.getMessage()
                );
            return HideSettings.DEFAULT;
        }
    }

    private void persistHideSettings(Player player, HideSettings settings) {
        this.context
            .plugin()
            .getServer()
            .getScheduler()
            .runTaskAsynchronously(this.context.plugin(), task -> {
                try {
                    this.repository.updateHideSettings(
                        player.getUniqueId(),
                        settings
                    );
                } catch (Exception exception) {
                    this.context
                        .plugin()
                        .getLogger()
                        .warning(
                            "Не удалось сохранить hide-настройки игрока " +
                                player.getName() +
                                ": " +
                                exception.getMessage()
                        );
                }
            });
    }

    public void applyNametagState(Player target) {
        if (applyTabNametagState(target)) {
            return;
        }

        for (Player viewer : this.context.plugin().getServer().getOnlinePlayers()) {
            applyNametagEntry(target, viewer.getScoreboard(), viewer);
        }
    }

    public void applyNametagStateForViewer(Player viewer) {
        applyNametagStateForViewer(viewer, applyTabNametagStateForViewer(viewer));
    }

    private void applyNametagStateForViewer(Player viewer, boolean tabHandled) {
        if (tabHandled) {
            return;
        }

        Scoreboard scoreboard = viewer.getScoreboard();

        if (scoreboard == null) {
            return;
        }

        for (Player onlinePlayer : this.context.plugin().getServer().getOnlinePlayers()) {
            applyNametagEntry(onlinePlayer, scoreboard, viewer);
        }
    }

    private void applyNametagEntry(
        Player target,
        Scoreboard scoreboard,
        Player viewer
    ) {
        if (scoreboard == null) {
            return;
        }

        String entry = target.getName();
        Team team = scoreboard.getTeam(HIDDEN_NAMETAG_TEAM);
        Team currentTeam = scoreboard.getEntryTeam(entry);

        if (shouldHideNametagFrom(target, viewer)) {
            if (currentTeam != null && currentTeam != team) {
                return;
            }

            if (team == null) {
                team = scoreboard.registerNewTeam(HIDDEN_NAMETAG_TEAM);
                team.setOption(
                    Team.Option.NAME_TAG_VISIBILITY,
                    Team.OptionStatus.NEVER
                );
            }

            if (currentTeam != team) {
                team.addEntry(entry);
            }
            return;
        }

        if (team == null || currentTeam != team) {
            return;
        }

        team.removeEntry(entry);

        if (team.getEntries().isEmpty()) {
            team.unregister();
        }
    }

    private boolean applyTabNametagState(Player target) {
        if (!Bukkit.getPluginManager().isPluginEnabled("TAB")) {
            return false;
        }

        TabAPI tabApi = TabAPI.getInstance();
        TabPlayer tabPlayer = tabApi.getPlayer(target.getUniqueId());
        NameTagManager nameTagManager = tabApi.getNameTagManager();

        if (tabPlayer == null || nameTagManager == null) {
            return false;
        }

        nameTagManager.showNameTag(tabPlayer);

        for (Player viewer : this.context.plugin().getServer().getOnlinePlayers()) {
            TabPlayer tabViewer = tabApi.getPlayer(viewer.getUniqueId());

            if (tabViewer == null) {
                continue;
            }

            if (shouldHideNametagFrom(target, viewer)) {
                nameTagManager.hideNameTag(tabPlayer, tabViewer);
                continue;
            }

            nameTagManager.showNameTag(tabPlayer, tabViewer);
        }

        return true;
    }

    private boolean applyTabNametagStateForViewer(Player viewer) {
        if (!Bukkit.getPluginManager().isPluginEnabled("TAB")) {
            return false;
        }

        TabAPI tabApi = TabAPI.getInstance();
        TabPlayer tabViewer = tabApi.getPlayer(viewer.getUniqueId());
        NameTagManager nameTagManager = tabApi.getNameTagManager();

        if (tabViewer == null || nameTagManager == null) {
            return false;
        }

        for (Player target : this.context.plugin().getServer().getOnlinePlayers()) {
            TabPlayer tabTarget = tabApi.getPlayer(target.getUniqueId());

            if (tabTarget == null) {
                continue;
            }

            if (shouldHideNametagFrom(target, viewer)) {
                nameTagManager.hideNameTag(tabTarget, tabViewer);
                continue;
            }

            nameTagManager.showNameTag(tabTarget, tabViewer);
        }

        return true;
    }

    private boolean shouldHideNametagFrom(Player target, Player viewer) {
        return !target.getUniqueId().equals(viewer.getUniqueId()) &&
        hidesNametag(target) &&
        !viewer.hasPermission(NAMETAG_BYPASS_PERMISSION);
    }

    private String resolveIpAddress(Player player) {
        InetSocketAddress address = player.getAddress();

        if (address == null || address.getAddress() == null) {
            return "unknown";
        }

        return address.getAddress().getHostAddress();
    }

    private Component render(String path, Map<String, String> placeholders) {
        return this.messageFormatter.render(path, placeholders);
    }

    private Component renderCopyable(String path, Map<String, String> placeholders, String copyValue) {
        String hoverText = this.messageFormatter.getOrDefault(
            "player-data.copy-hover",
            "Нажмите, чтобы скопировать"
        );
        return render(path, placeholders)
            .hoverEvent(HoverEvent.showText(Component.text(hoverText)))
            .clickEvent(ClickEvent.copyToClipboard(copyValue));
    }

    private Long consumeSuppressJoinQuitUntil(Player player) {
        try {
            Optional<PlayerProfileRecord> profile = this.repository.findByUuid(
                player.getUniqueId()
            );

            if (profile.isEmpty()) {
                return null;
            }

            Long suppressUntil = profile.get().suppressJoinQuitUntil();

            if (suppressUntil != null) {
                this.context
                    .plugin()
                    .getServer()
                    .getScheduler()
                    .runTaskAsynchronously(this.context.plugin(), task -> {
                        try {
                            this.repository.updateSuppressJoinQuitUntil(
                                    player.getUniqueId(),
                                    null
                                );
                        } catch (Exception exception) {
                            this.context
                                .plugin()
                                .getLogger()
                                .warning(
                                    "Не удалось сбросить suppress-флаг world-перехода для игрока " +
                                    player.getName() +
                                    ": " +
                                    exception.getMessage()
                                );
                        }
                    });
            }

            return suppressUntil;
        } catch (Exception exception) {
            this.context
                .plugin()
                .getLogger()
                .warning(
                    "Не удалось загрузить suppress-флаг world-перехода для игрока " +
                    player.getName() +
                    ": " +
                    exception.getMessage()
                );
            return null;
        }
    }

    private String formatTimestamp(long epochMillis) {
        String pattern = this.messageFormatter.getOrDefault(
            "player-data.date-format",
            "dd.MM.yyyy HH:mm:ss"
        );

        return DateTimeFormatter.ofPattern(pattern)
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(epochMillis));
    }
}
