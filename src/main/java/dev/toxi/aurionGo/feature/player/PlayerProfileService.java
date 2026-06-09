package dev.toxi.aurionGo.feature.player;

import dev.toxi.aurionGo.shared.AurionContext;
import dev.toxi.aurionGo.storage.player.PlayerProfileRecord;
import dev.toxi.aurionGo.storage.player.PlayerProfileRepository;
import dev.toxi.aurionGo.storage.player.PlayerProfileSnapshot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

public final class PlayerProfileService {
    private final AurionContext context;
    private final PlayerProfileRepository repository;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public PlayerProfileService(AurionContext context, PlayerProfileRepository repository) {
        this.context = context;
        this.repository = repository;
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

        this.context.plugin().getServer().getScheduler().runTaskAsynchronously(this.context.plugin(), task -> {
            try {
                boolean firstJoin = this.repository.saveOrUpdateJoin(snapshot);

                if (firstJoin) {
                    this.context.plugin().getServer().getScheduler().runTask(this.context.plugin(), syncTask ->
                            this.context.plugin().getServer().broadcast(render(
                                    "player-data.first-join",
                                    Map.of("player", player.getName())
                            ))
                    );
                }
            } catch (Exception exception) {
                this.context.plugin().getLogger().warning("Не удалось сохранить профиль игрока " + player.getName() + ": " + exception.getMessage());
            }
        });
    }

    public void sendPlayerInfo(CommandSender sender, String nickname) {
        this.context.plugin().getServer().getScheduler().runTaskAsynchronously(this.context.plugin(), task -> {
            try {
                Optional<PlayerProfileRecord> optionalProfile = this.repository.findByNickname(nickname);
                this.context.plugin().getServer().getScheduler().runTask(this.context.plugin(), syncTask -> {
                    if (optionalProfile.isEmpty()) {
                        sender.sendMessage(render("player-data.not-found", Map.of("player", nickname)));
                        return;
                    }

                    PlayerProfileRecord profile = optionalProfile.get();
                    sender.sendMessage(render("player-data.header", Map.of("player", profile.nickname())));
                    sender.sendMessage(render("player-data.nickname", Map.of("value", profile.nickname())));
                    sender.sendMessage(render("player-data.uuid", Map.of("value", profile.uuid().toString())));
                    sender.sendMessage(render("player-data.first-join-line", Map.of("value", formatTimestamp(profile.firstJoin()))));
                    sender.sendMessage(render("player-data.last-join-line", Map.of("value", formatTimestamp(profile.lastJoin()))));
                    sender.sendMessage(render("player-data.ip-line", Map.of("value", profile.ipAddress())));
                });
            } catch (Exception exception) {
                this.context.plugin().getServer().getScheduler().runTask(this.context.plugin(), syncTask ->
                        sender.sendMessage(render("player-data.lookup-error", Map.of("error", exception.getMessage())))
                );
            }
        });
    }

    public void sendUsage(CommandSender sender) {
        sender.sendMessage(render("player-data.usage", Map.of()));
    }

    public void sendNoPermission(CommandSender sender) {
        sender.sendMessage(render("errors.no-permission", Map.of()));
    }

    private String resolveIpAddress(Player player) {
        InetSocketAddress address = player.getAddress();

        if (address == null || address.getAddress() == null) {
            return "unknown";
        }

        return address.getAddress().getHostAddress();
    }

    private Component render(String path, Map<String, String> placeholders) {
        String template = this.context.configManager()
                .require("messages")
                .configuration()
                .getString(path, "<color:#FF4F4F>В конфиге отсутствует сообщение: " + path);
        String resolved = template;

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolved = resolved.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        return this.miniMessage.deserialize(resolved);
    }

    private String formatTimestamp(long epochMillis) {
        String pattern = this.context.configManager()
                .require("messages")
                .configuration()
                .getString("player-data.date-format", "dd.MM.yyyy HH:mm:ss");

        return DateTimeFormatter.ofPattern(pattern)
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(epochMillis));
    }
}
