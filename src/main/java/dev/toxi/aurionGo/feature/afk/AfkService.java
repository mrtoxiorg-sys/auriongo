package dev.toxi.aurionGo.feature.afk;

import dev.toxi.aurionGo.config.ConfigFile;
import dev.toxi.aurionGo.config.StandardConfigs;
import dev.toxi.aurionGo.feature.integration.SuperVanishBridge;
import dev.toxi.aurionGo.feature.player.PlayerProfileService;
import dev.toxi.aurionGo.message.MessageFormatter;
import dev.toxi.aurionGo.shared.AurionContext;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class AfkService {

    private final AurionContext context;
    private final ConfigFile afkConfig;
    private final MessageFormatter messageFormatter;
    private final PlayerProfileService playerProfileService;
    private final Set<UUID> afkPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private BukkitTask autoAfkTask;

    public AfkService(AurionContext context) {
        this.context = context;
        this.afkConfig = context.configManager().require(StandardConfigs.AFK);
        this.messageFormatter = context
            .serviceRegistry()
            .require(MessageFormatter.class);
        this.playerProfileService = context
            .serviceRegistry()
            .require(PlayerProfileService.class);
    }

    /**
     * Запускает периодическую проверку бездействия, если авто-АФК включен
     * в конфигурации. Должен вызываться в основном потоке при включении модуля.
     */
    public void start() {
        if (
            !this.afkConfig.configuration().getBoolean("auto-afk.enabled", true)
        ) {
            return;
        }

        long checkIntervalTicks = 20L;
        this.autoAfkTask = this.context
            .plugin()
            .getServer()
            .getScheduler()
            .runTaskTimer(
                this.context.plugin(),
                this::scanIdlePlayers,
                checkIntervalTicks,
                checkIntervalTicks
            );
    }

    public boolean isAfk(Player player) {
        return this.afkPlayers.contains(player.getUniqueId());
    }

    /**
     * Переключает АФК-режим по команде /afk. Возвращает новое состояние.
     */
    public boolean toggle(Player player) {
        if (this.afkPlayers.remove(player.getUniqueId())) {
            this.lastActivity.put(
                player.getUniqueId(),
                System.currentTimeMillis()
            );
            announceReturn(player);
            return false;
        }

        this.afkPlayers.add(player.getUniqueId());
        broadcast("afk.now-afk", player);
        player.sendMessage(render("afk.self-enabled", player));
        return true;
    }

    /**
     * Фиксирует активность игрока и выводит его из АФК при необходимости.
     * Вызывается из синхронных событий в основном потоке.
     */
    public void recordActivity(Player player) {
        this.lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
        wake(player);
    }

    /**
     * Версия {@link #recordActivity(Player)} для асинхронных событий (чат):
     * работа с миром откладывается в основной поток.
     */
    public void recordActivityAsync(Player player) {
        this.lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
        wakeFromAsync(player);
    }

    /**
     * Переводит игрока в АФК из-за бездействия. Не требует прав на /afk.
     * Должен вызываться в основном потоке.
     */
    public void autoAfk(Player player) {
        if (!this.afkPlayers.add(player.getUniqueId())) {
            return;
        }

        broadcast("afk.now-afk", player);
        player.sendMessage(render("afk.self-auto", player));
    }

    private void scanIdlePlayers() {
        long now = System.currentTimeMillis();
        long idleMillis =
            this.afkConfig
                .configuration()
                .getLong("auto-afk.idle-seconds", 300L) * 1000L;

        if (idleMillis <= 0L) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isAfk(player)) {
                continue;
            }

            long last = this.lastActivity.computeIfAbsent(
                player.getUniqueId(),
                key -> now
            );

            if (now - last >= idleMillis) {
                autoAfk(player);
            }
        }
    }

    /**
     * Выводит игрока из АФК из-за движения или действия. Ничего не делает,
     * если игрок не был в АФК. Должен вызываться в основном потоке.
     */
    public void wake(Player player) {
        if (this.afkPlayers.remove(player.getUniqueId())) {
            announceReturn(player);
        }
    }

    /**
     * Безопасный выход из АФК для асинхронных событий (например, чата):
     * откладывает работу с миром в основной поток.
     */
    public void wakeFromAsync(Player player) {
        if (!isAfk(player)) {
            return;
        }

        this.context
            .plugin()
            .getServer()
            .getScheduler()
            .runTask(this.context.plugin(), () -> wake(player));
    }

    public void clear(Player player) {
        this.afkPlayers.remove(player.getUniqueId());
        this.lastActivity.remove(player.getUniqueId());
    }

    public void shutdown() {
        if (this.autoAfkTask != null) {
            this.autoAfkTask.cancel();
            this.autoAfkTask = null;
        }

        this.afkPlayers.clear();
        this.lastActivity.clear();
    }

    public Component renderNoPermission() {
        return this.messageFormatter.render("errors.no-permission", Map.of());
    }

    public Component renderPlayerOnly() {
        return this.messageFormatter.render("errors.player-only", Map.of());
    }

    private void announceReturn(Player player) {
        broadcast("afk.return", player);
        player.sendMessage(render("afk.self-disabled", player));
    }

    private void broadcast(String path, Player player) {
        if (
            this.playerProfileService.hidesAfkMessages(player) ||
            SuperVanishBridge.isVanished(this.context.plugin(), player)
        ) {
            return;
        }

        Component message = render(path, player);

        for (Player recipient : Bukkit.getOnlinePlayers()) {
            recipient.sendMessage(message);
        }

        Bukkit.getConsoleSender().sendMessage(message);
    }

    private Component render(String path, Player player) {
        return this.messageFormatter.render(
            path,
            Map.of("player", player.getName())
        );
    }
}
