package dev.toxi.aurionGo.feature.world;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import dev.toxi.aurionGo.config.ConfigFile;
import dev.toxi.aurionGo.config.StandardConfigs;
import dev.toxi.aurionGo.feature.chat.ChatService;
import dev.toxi.aurionGo.feature.player.PlayerProfileService;
import dev.toxi.aurionGo.message.MessageFormatter;
import dev.toxi.aurionGo.shared.AurionContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class WorldService implements PluginMessageListener {

    private static final String CHANNEL = "BungeeCord";
    private static final long DEFAULT_LOOKUP_TIMEOUT_MILLIS = 5_000L;

    private final AurionContext context;
    private final ConfigFile worldConfig;
    private final MessageFormatter formatter;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> combatTags = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pendingLookups = new ConcurrentHashMap<>();

    public WorldService(AurionContext context) {
        this.context = context;
        this.worldConfig = context.configManager().require(StandardConfigs.WORLD);
        this.formatter = context.serviceRegistry().require(MessageFormatter.class);
    }

    public void enable() {
        this.context
            .plugin()
            .getServer()
            .getMessenger()
            .registerOutgoingPluginChannel(this.context.plugin(), CHANNEL);
        this.context
            .plugin()
            .getServer()
            .getMessenger()
            .registerIncomingPluginChannel(this.context.plugin(), CHANNEL, this);
    }

    public void disable() {
        this.context
            .plugin()
            .getServer()
            .getMessenger()
            .unregisterIncomingPluginChannel(this.context.plugin(), CHANNEL, this);
        this.context
            .plugin()
            .getServer()
            .getMessenger()
            .unregisterOutgoingPluginChannel(this.context.plugin(), CHANNEL);
        this.cooldowns.clear();
        this.combatTags.clear();
        this.pendingLookups.clear();
    }

    public void switchPlayer(Player player) {
        long now = System.currentTimeMillis();
        Settings settings = readSettings();

        if (!settings.isValid()) {
            player.sendMessage(render("world.invalid-config", Map.of()));
            return;
        }

        long pendingUntil = this.pendingLookups.getOrDefault(
            player.getUniqueId(),
            0L
        );

        if (pendingUntil > now) {
            player.sendMessage(render("world.lookup-pending", Map.of()));
            return;
        }

        this.pendingLookups.remove(player.getUniqueId());

        long combatUntil = this.combatTags.getOrDefault(player.getUniqueId(), 0L);

        if (combatUntil > now) {
            player.sendMessage(
                render(
                    "world.combat-block",
                    Map.of("seconds", String.valueOf(ceilSeconds(combatUntil - now)))
                )
            );
            return;
        }

        this.combatTags.remove(player.getUniqueId());

        long cooldownUntil = this.cooldowns.getOrDefault(player.getUniqueId(), 0L);

        if (cooldownUntil > now) {
            player.sendMessage(
                render(
                    "world.cooldown",
                    Map.of(
                        "seconds",
                        String.valueOf(ceilSeconds(cooldownUntil - now))
                    )
                )
            );
            return;
        }

        this.cooldowns.remove(player.getUniqueId());
        this.pendingLookups.put(player.getUniqueId(), now + settings.lookupTimeoutMillis());
        player.sendMessage(render("world.checking", Map.of()));

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("GetServer");
        player.sendPluginMessage(this.context.plugin(), CHANNEL, out.toByteArray());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) {
            return;
        }

        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String subChannel = in.readUTF();

        if (!"GetServer".equals(subChannel)) {
            return;
        }

        Long pendingUntil = this.pendingLookups.remove(player.getUniqueId());

        if (pendingUntil == null || pendingUntil < System.currentTimeMillis()) {
            return;
        }

        String currentServer = in.readUTF();
        Settings settings = readSettings();

        if (!settings.isValid()) {
            player.sendMessage(render("world.invalid-config", Map.of()));
            return;
        }

        String targetServer = settings.resolveTarget(currentServer);

        if (targetServer == null) {
            player.sendMessage(
                render(
                    "world.unsupported-server",
                    Map.of("server", currentServer)
                )
            );
            return;
        }

        player.getWorld().spawnParticle(
            Particle.PORTAL,
            player.getLocation().add(0, 1, 0),
            100,
            0.5,
            1.0,
            0.5,
            0.1
        );
        player.playSound(
            player.getLocation(),
            Sound.ENTITY_ENDERMAN_TELEPORT,
            1.0f,
            1.0f
        );
        player.sendMessage(
            render(
                "world.connecting",
                Map.of("server", settings.resolveDisplayName(targetServer))
            )
        );

        this.context
            .plugin()
            .getServer()
            .getScheduler()
            .runTaskLater(this.context.plugin(), () -> {
                if (!player.isOnline()) {
                    return;
                }

                long now = System.currentTimeMillis();
                long combatUntil = this.combatTags.getOrDefault(
                    player.getUniqueId(),
                    0L
                );

                if (combatUntil > now) {
                    player.sendMessage(
                        render(
                            "world.combat-block",
                            Map.of(
                                "seconds",
                                String.valueOf(ceilSeconds(combatUntil - now))
                            )
                        )
                    );
                    return;
                }

                this.cooldowns.put(
                    player.getUniqueId(),
                    now + (settings.cooldownSeconds() * 1000L)
                );

                ChatService chatService = resolveChatService();

                if (chatService != null) {
                    chatService.broadcastWorldSwitch(
                        player,
                        settings.resolveDisplayName(currentServer),
                        settings.resolveDisplayName(targetServer)
                    );
                }

                PlayerProfileService profileService = resolvePlayerProfileService();

                if (profileService != null) {
                    profileService.suppressNextJoinQuitForWorldSwitch(player);
                }

                ByteArrayDataOutput out = ByteStreams.newDataOutput();
                out.writeUTF("Connect");
                out.writeUTF(targetServer);
                player.sendPluginMessage(
                    this.context.plugin(),
                    CHANNEL,
                    out.toByteArray()
                );
            }, settings.connectDelayTicks());
    }

    public void tagCombat(Player player) {
        long combatSeconds = Math.max(0, readSettings().combatBlockSeconds());

        if (combatSeconds <= 0) {
            this.combatTags.remove(player.getUniqueId());
            return;
        }

        this.combatTags.put(
            player.getUniqueId(),
            System.currentTimeMillis() + (combatSeconds * 1000L)
        );
    }

    public void clear(Player player) {
        UUID uniqueId = player.getUniqueId();
        this.pendingLookups.remove(uniqueId);
        this.cooldowns.remove(uniqueId);
        this.combatTags.remove(uniqueId);
    }

    public Component renderNoPermission() {
        return render("errors.no-permission", Map.of());
    }

    public Component renderPlayerOnly() {
        return render("errors.player-only", Map.of());
    }

    private ChatService resolveChatService() {
        try {
            return this.context.serviceRegistry().require(ChatService.class);
        } catch (IllegalStateException exception) {
            return null;
        }
    }

    private PlayerProfileService resolvePlayerProfileService() {
        try {
            return this.context.serviceRegistry().require(PlayerProfileService.class);
        } catch (IllegalStateException exception) {
            return null;
        }
    }

    private Component render(String path, Map<String, String> placeholders) {
        return this.formatter.render(path, placeholders);
    }

    private Settings readSettings() {
        List<String> servers = this.worldConfig
            .configuration()
            .getStringList("servers")
            .stream()
            .map(String::trim)
            .filter(server -> !server.isEmpty())
            .distinct()
            .toList();
        int cooldownSeconds = Math.max(
            0,
            this.worldConfig.configuration().getInt("cooldown-seconds", 10)
        );
        int combatBlockSeconds = Math.max(
            0,
            this.worldConfig.configuration().getInt("combat-block-seconds", 15)
        );
        long connectDelayTicks = Math.max(
            0L,
            this.worldConfig.configuration().getLong("connect-delay-ticks", 10L)
        );
        long lookupTimeoutMillis = Math.max(
            1000L,
            this.worldConfig
                .configuration()
                .getLong(
                    "lookup-timeout-millis",
                    DEFAULT_LOOKUP_TIMEOUT_MILLIS
                )
        );
        Map<String, String> displayNames = new LinkedHashMap<>();

        for (String server : servers) {
            String configuredDisplayName = this.worldConfig
                .configuration()
                .getString("display-names." + server);

            displayNames.put(
                server.toLowerCase(java.util.Locale.ROOT),
                configuredDisplayName == null || configuredDisplayName.isBlank()
                    ? server
                    : configuredDisplayName.trim()
            );
        }

        return new Settings(
            servers,
            cooldownSeconds,
            combatBlockSeconds,
            connectDelayTicks,
            lookupTimeoutMillis,
            displayNames
        );
    }

    private long ceilSeconds(long millisLeft) {
        return Math.max(1L, (millisLeft + 999L) / 1000L);
    }

    private record Settings(
        List<String> servers,
        int cooldownSeconds,
        int combatBlockSeconds,
        long connectDelayTicks,
        long lookupTimeoutMillis,
        Map<String, String> displayNames
    ) {
        private boolean isValid() {
            return this.servers.size() == 2;
        }

        private String resolveTarget(String currentServer) {
            if (!isValid()) {
                return null;
            }

            if (this.servers.get(0).equalsIgnoreCase(currentServer)) {
                return this.servers.get(1);
            }

            if (this.servers.get(1).equalsIgnoreCase(currentServer)) {
                return this.servers.get(0);
            }

            return null;
        }

        private String resolveDisplayName(String server) {
            return this.displayNames.getOrDefault(
                    server.toLowerCase(java.util.Locale.ROOT),
                    server
                );
        }
    }
}
