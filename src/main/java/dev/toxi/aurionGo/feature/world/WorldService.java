package dev.toxi.aurionGo.feature.world;

import dev.toxi.aurionGo.config.StandardConfigs;
import dev.toxi.aurionGo.feature.chat.ChatService;
import dev.toxi.aurionGo.feature.player.PlayerProfileService;
import dev.toxi.aurionGo.message.MessageFormatter;
import dev.toxi.aurionGo.shared.AurionContext;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public final class WorldService {

    private final AurionContext context;
    private final MessageFormatter messageFormatter;
    private final WorldSettings settings;
    private final WorldSwitchGuard guard;
    private final WorldMessenger messenger;

    public WorldService(AurionContext context) {
        this.context = context;
        this.messageFormatter = context
            .serviceRegistry()
            .require(MessageFormatter.class);
        this.settings = WorldSettings.from(
            context.configManager().require(StandardConfigs.WORLD).configuration()
        );
        this.guard = new WorldSwitchGuard(this.settings);
        this.messenger = new WorldMessenger(context.plugin(), this::onCurrentServer);
    }

    public void enable() {
        this.messenger.register();
    }

    public void disable() {
        this.messenger.unregister();
        this.guard.clear();
    }

    public MessageFormatter formatter() {
        return this.messageFormatter;
    }

    public void markCombat(Player player) {
        this.guard.markCombat(player.getUniqueId());
    }

    public void forget(Player player) {
        this.guard.forget(player.getUniqueId());
    }

    public void requestSwitch(Player player) {
        if (!this.settings.isValid()) {
            player.sendMessage(render("world.invalid-config", Map.of()));
            return;
        }

        UUID uniqueId = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (this.guard.isPending(uniqueId, now)) {
            player.sendMessage(render("world.lookup-pending", Map.of()));
            return;
        }

        if (rejectByTimer(player, "world.combat-block", this.guard.combatRemaining(uniqueId, now))) {
            return;
        }

        if (rejectByTimer(player, "world.cooldown", this.guard.cooldownRemaining(uniqueId, now))) {
            return;
        }

        this.guard.markPending(uniqueId);
        player.sendMessage(render("world.checking", Map.of()));
        this.messenger.requestCurrentServer(player);
    }

    private boolean rejectByTimer(Player player, String path, long remainingSeconds) {
        if (remainingSeconds <= 0L) {
            return false;
        }

        player.sendMessage(
            render(path, Map.of("seconds", Long.toString(remainingSeconds)))
        );
        return true;
    }

    private void onCurrentServer(Player player, String currentServer) {
        if (!this.guard.consumePending(player.getUniqueId())) {
            return;
        }

        if (!this.settings.contains(currentServer)) {
            player.sendMessage(
                render("world.unsupported-server", Map.of("server", currentServer))
            );
            return;
        }

        String targetServer = this.settings.nextServer(currentServer);

        if (targetServer == null) {
            player.sendMessage(render("world.invalid-config", Map.of()));
            return;
        }

        this.guard.markCooldown(player.getUniqueId());
        player.sendMessage(
            render(
                "world.connecting",
                Map.of("server", this.settings.displayName(targetServer))
            )
        );
        announceSwitch(player, currentServer, targetServer);
        scheduleConnect(player, targetServer);
    }

    private void scheduleConnect(Player player, String targetServer) {
        UUID uniqueId = player.getUniqueId();

        this.context
            .plugin()
            .getServer()
            .getScheduler()
            .runTaskLater(
                this.context.plugin(),
                () -> connectIfOnline(uniqueId, targetServer),
                this.settings.connectDelayTicks()
            );
    }

    private void connectIfOnline(UUID uniqueId, String targetServer) {
        Player online = this.context.plugin().getServer().getPlayer(uniqueId);

        if (online == null || !online.isOnline()) {
            return;
        }

        this.messenger.connect(online, targetServer);
    }

    private void announceSwitch(
        Player player,
        String fromServer,
        String targetServer
    ) {
        PlayerProfileService profileService = optionalService(
            PlayerProfileService.class
        );

        if (profileService != null) {
            profileService.suppressNextJoinQuitForWorldSwitch(player);
        }

        ChatService chatService = optionalService(ChatService.class);

        if (chatService != null) {
            chatService.broadcastWorldSwitch(
                player,
                this.settings.displayName(fromServer),
                this.settings.displayName(targetServer)
            );
        }
    }

    private <T> T optionalService(Class<T> type) {
        try {
            return this.context.serviceRegistry().require(type);
        } catch (IllegalStateException exception) {
            return null;
        }
    }

    private Component render(String path, Map<String, String> placeholders) {
        return this.messageFormatter.render(path, placeholders);
    }
}
