package dev.toxi.aurionGo.feature.servermonitor;

import dev.toxi.aurionGo.config.ConfigFile;
import dev.toxi.aurionGo.config.StandardConfigs;
import dev.toxi.aurionGo.feature.player.PlayerProfileService;
import dev.toxi.aurionGo.message.MessageFormatter;
import dev.toxi.aurionGo.shared.AurionContext;
import java.lang.management.ManagementFactory;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

public final class ServerMonitorService implements Listener {

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat(
        "0.0"
    );
    private static final String OBJECTIVE_NAME = "auriongo_monitor";

    private final AurionContext context;
    private final ConfigFile config;
    private final MessageFormatter formatter;
    private final PlayerProfileService playerProfileService;
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(
        Snapshot.empty()
    );
    private final Set<UUID> enabledPlayers = ConcurrentHashMap.newKeySet();

    private BukkitTask sampleTask;
    private BukkitTask renderTask;

    public ServerMonitorService(AurionContext context) {
        this.context = context;
        this.config = context
            .configManager()
            .require(StandardConfigs.SERVER_MONITOR);
        this.formatter = context
            .serviceRegistry()
            .require(MessageFormatter.class);
        this.playerProfileService = context
            .serviceRegistry()
            .require(PlayerProfileService.class);
    }

    public void enable() {
        this.context
            .plugin()
            .getServer()
            .getPluginManager()
            .registerEvents(this, this.context.plugin());
        this.sampleTask = this.context
            .plugin()
            .getServer()
            .getScheduler()
            .runTaskTimerAsynchronously(
                this.context.plugin(),
                this::refreshSnapshot,
                1L,
                sampleIntervalTicks()
            );
        this.renderTask = this.context
            .plugin()
            .getServer()
            .getScheduler()
            .runTaskTimer(
                this.context.plugin(),
                this::renderEnabledPlayers,
                20L,
                renderIntervalTicks()
            );
    }

    public void disable() {
        HandlerList.unregisterAll(this);

        if (this.sampleTask != null) {
            this.sampleTask.cancel();
            this.sampleTask = null;
        }

        if (this.renderTask != null) {
            this.renderTask.cancel();
            this.renderTask = null;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            clearBoard(player);
        }

        this.enabledPlayers.clear();
    }

    public MessageFormatter formatter() {
        return this.formatter;
    }

    public boolean toggle(Player player) {
        UUID uniqueId = player.getUniqueId();

        if (this.enabledPlayers.remove(uniqueId)) {
            clearBoard(player);
            return false;
        }

        this.enabledPlayers.add(uniqueId);
        render(player, this.snapshot.get());
        return true;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (this.enabledPlayers.contains(player.getUniqueId())) {
            render(player, this.snapshot.get());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        this.enabledPlayers.remove(event.getPlayer().getUniqueId());
    }

    private void refreshSnapshot() {
        double[] tps = Bukkit.getTPS();
        double tpsValue = tps.length == 0 ? 20.0D : tps[0];
        double mspt = Bukkit.getAverageTickTime();
        double cpuLoad = cpuLoadPercent();
        double memoryUsage = usedMemoryPercent();
        int online = Bukkit.getOnlinePlayers().size();
        int maxPlayers = Bukkit.getMaxPlayers();
        double averagePing = averagePing();
        this.snapshot.set(
            new Snapshot(
                tpsValue,
                mspt,
                cpuLoad,
                memoryUsage,
                online,
                maxPlayers,
                averagePing
            )
        );
    }

    private void renderEnabledPlayers() {
        Snapshot current = this.snapshot.get();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (this.enabledPlayers.contains(player.getUniqueId())) {
                render(player, current);
            }
        }
    }

    private void render(Player player, Snapshot current) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();

        if (manager == null) {
            return;
        }

        Scoreboard scoreboard = manager.getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective(
            OBJECTIVE_NAME,
            Criteria.DUMMY,
            title()
        );
        objective.setDisplaySlot(org.bukkit.scoreboard.DisplaySlot.SIDEBAR);

        List<Component> lines = buildLines(current);
        int score = lines.size();

        for (int index = 0; index < lines.size(); index++) {
            Component line = lines.get(index);
            String entry = entryKey(index);
            Team team = scoreboard.registerNewTeam("sm-line-" + index);
            team.addEntry(entry);
            team.prefix(line);
            objective.getScore(entry).setScore(score--);
        }

        player.setScoreboard(scoreboard);
        this.playerProfileService.applyNametagStateForViewer(player);
    }

    private String entryKey(int index) {
        return "§" + Integer.toHexString(index) + "§r";
    }

    private void clearBoard(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();

        if (manager != null) {
            player.setScoreboard(manager.getMainScoreboard());
        }
    }

    private Component title() {
        String raw = this.config
            .configuration()
            .getString(
                "scoreboard.title",
                "<fcolor>✦</fcolor> <white>Server Monitor</white>"
            );
        return this.formatter.renderRaw(raw, Map.of());
    }

    private List<Component> buildLines(Snapshot current) {
        List<Component> lines = new ArrayList<>();
        List<String> templates = this.config
            .configuration()
            .getStringList("scoreboard.lines");

        if (templates.isEmpty()) {
            templates = List.of(
                "<dark_gray>┏━━━━━━━━━━━━━━━━┓",
                "<gray>  Актуальное состояние сервера",
                " ",
                "<fcolor>▸ TPS</fcolor> <dark_gray>•</dark_gray> <white>{tps}</white>",
                "<fcolor>▸ MSPT</fcolor> <dark_gray>•</dark_gray> <white>{mspt}</white>",
                "<fcolor>▸ Online</fcolor> <dark_gray>•</dark_gray> <white>{online}/{max_online}</white>",
                "<fcolor>▸ CPU</fcolor> <dark_gray>•</dark_gray> <white>{cpu}%</white>",
                "<fcolor>▸ RAM</fcolor> <dark_gray>•</dark_gray> <white>{memory}%</white>",
                "<fcolor>▸ Ping</fcolor> <dark_gray>•</dark_gray> <white>{ping}ms</white>",
                " ",
                "<dark_gray>┗━━━━━━━━━━━━━━━━┛"
            );
        }

        Map<String, String> placeholders = Map.of(
            "tps",
            formatMetric(current.tps),
            "mspt",
            formatMetric(current.mspt),
            "online",
            Integer.toString(current.online),
            "max_online",
            Integer.toString(current.maxPlayers),
            "cpu",
            formatMetric(current.cpuLoad),
            "memory",
            formatMetric(current.memoryUsage),
            "ping",
            formatMetric(current.averagePing)
        );

        for (String template : templates) {
            lines.add(this.formatter.renderRaw(template, placeholders));
        }

        return lines;
    }

    private long sampleIntervalTicks() {
        return Math.max(
            20L,
            this.config
                .configuration()
                .getLong("update.sample-interval-ticks", 40L)
        );
    }

    private long renderIntervalTicks() {
        return Math.max(
            20L,
            this.config
                .configuration()
                .getLong("update.render-interval-ticks", 40L)
        );
    }

    private String formatMetric(double value) {
        return DECIMAL_FORMAT.format(value);
    }

    private double averagePing() {
        int players = 0;
        long total = 0L;

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            players++;
            total += Math.max(0, onlinePlayer.getPing());
        }

        if (players == 0) {
            return 0.0D;
        }

        return (double) total / players;
    }

    private double usedMemoryPercent() {
        Runtime runtime = Runtime.getRuntime();
        long max = runtime.maxMemory();
        long used = runtime.totalMemory() - runtime.freeMemory();

        if (max <= 0L) {
            return 0.0D;
        }

        return ((double) used / max) * 100.0D;
    }

    private double cpuLoadPercent() {
        java.lang.management.OperatingSystemMXBean operatingSystemMXBean =
            ManagementFactory.getOperatingSystemMXBean();

        if (
            operatingSystemMXBean instanceof
                com.sun.management.OperatingSystemMXBean sunBean
        ) {
            double load = sunBean.getCpuLoad();
            if (load >= 0.0D) {
                return load * 100.0D;
            }
        }

        return 0.0D;
    }

    private record Snapshot(
        double tps,
        double mspt,
        double cpuLoad,
        double memoryUsage,
        int online,
        int maxPlayers,
        double averagePing
    ) {
        private static Snapshot empty() {
            return new Snapshot(
                20.0D,
                0.0D,
                0.0D,
                0.0D,
                0,
                Bukkit.getMaxPlayers(),
                0.0D
            );
        }
    }
}
