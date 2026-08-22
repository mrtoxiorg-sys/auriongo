package dev.toxi.aurionGo.feature.integration;

import dev.toxi.aurionGo.feature.player.PlayerProfileService;
import dev.toxi.aurionGo.shared.AurionContext;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;

final class VoiceChatSpeakingIndicator {

    private static final Component SPEAKER_ICON = MiniMessage.miniMessage()
        .deserialize("<sprite:\"minecraft:gui\":\"voicechat:icons/speaker\">");
    private static final long SPEAKING_TIMEOUT_NANOS = TimeUnit.MILLISECONDS.toNanos(300L);
    private static final double HEIGHT_ABOVE_EYES = 0.45D;

    private final AurionContext context;
    private final PlayerProfileService profileService;
    private final ConcurrentMap<VoiceRoute, Long> lastPackets = new ConcurrentHashMap<>();
    private final Map<UUID, Indicator> indicators = new HashMap<>();
    private BukkitTask updateTask;

    VoiceChatSpeakingIndicator(
        AurionContext context,
        PlayerProfileService profileService
    ) {
        this.context = context;
        this.profileService = profileService;
    }

    void enable() {
        if (this.updateTask != null) {
            return;
        }

        this.updateTask = this.context
            .plugin()
            .getServer()
            .getScheduler()
            .runTaskTimer(this.context.plugin(), this::tick, 1L, 1L);
    }

    void disable() {
        if (this.updateTask != null) {
            this.updateTask.cancel();
            this.updateTask = null;
        }

        for (Indicator indicator : this.indicators.values()) {
            indicator.display().remove();
        }

        this.indicators.clear();
        this.lastPackets.clear();
    }

    void markAudible(UUID speakerUuid, UUID viewerUuid) {
        if (speakerUuid.equals(viewerUuid)) {
            return;
        }

        this.lastPackets.put(
            new VoiceRoute(speakerUuid, viewerUuid),
            System.nanoTime()
        );
    }

    private void tick() {
        long now = System.nanoTime();
        Map<UUID, Set<UUID>> audibleViewers = new HashMap<>();

        for (Map.Entry<VoiceRoute, Long> entry : this.lastPackets.entrySet()) {
            VoiceRoute route = entry.getKey();
            long lastPacket = entry.getValue();

            if (now - lastPacket > SPEAKING_TIMEOUT_NANOS) {
                this.lastPackets.remove(route, lastPacket);
                continue;
            }

            audibleViewers
                .computeIfAbsent(route.speakerUuid(), ignored -> new HashSet<>())
                .add(route.viewerUuid());
        }

        for (UUID speakerUuid : new HashSet<>(this.indicators.keySet())) {
            if (!audibleViewers.containsKey(speakerUuid)) {
                removeIndicator(speakerUuid);
            }
        }

        for (Map.Entry<UUID, Set<UUID>> entry : audibleViewers.entrySet()) {
            updateIndicator(entry.getKey(), entry.getValue());
        }
    }

    private void updateIndicator(UUID speakerUuid, Set<UUID> viewerUuids) {
        Player speaker = Bukkit.getPlayer(speakerUuid);

        if (
            speaker == null ||
            !speaker.isOnline() ||
            !this.profileService.hidesNametag(speaker)
        ) {
            removeIndicator(speakerUuid);
            return;
        }

        Indicator indicator = ensureIndicator(speaker);
        indicator.display().teleport(indicatorLocation(speaker));
        syncViewers(speaker, viewerUuids, indicator);
    }

    private Indicator ensureIndicator(Player speaker) {
        Indicator current = this.indicators.get(speaker.getUniqueId());

        if (
            current != null &&
            current.display().isValid() &&
            current.display().getWorld().equals(speaker.getWorld())
        ) {
            return current;
        }

        removeIndicator(speaker.getUniqueId());

        TextDisplay display = speaker.getWorld().spawn(
            indicatorLocation(speaker),
            TextDisplay.class,
            this::configureDisplay
        );
        Indicator created = new Indicator(display, new HashSet<>());
        this.indicators.put(speaker.getUniqueId(), created);
        return created;
    }

    private void configureDisplay(TextDisplay display) {
        display.text(SPEAKER_ICON);
        display.setBillboard(Display.Billboard.CENTER);
        display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        display.setDefaultBackground(false);
        display.setShadowed(false);
        display.setSeeThrough(true);
        display.setGravity(false);
        display.setInvulnerable(true);
        display.setPersistent(false);
        display.setSilent(true);
        display.setVisibleByDefault(false);
        display.setTeleportDuration(1);
    }

    private void syncViewers(
        Player speaker,
        Set<UUID> viewerUuids,
        Indicator indicator
    ) {
        Set<UUID> visible = new HashSet<>();

        for (UUID viewerUuid : viewerUuids) {
            Player viewer = Bukkit.getPlayer(viewerUuid);

            if (viewer == null || !shouldSeeIndicator(speaker, viewer)) {
                continue;
            }

            visible.add(viewerUuid);

            if (!indicator.viewers().contains(viewerUuid)) {
                viewer.showEntity(this.context.plugin(), indicator.display());
            }
        }

        for (UUID viewerUuid : new HashSet<>(indicator.viewers())) {
            if (visible.contains(viewerUuid)) {
                continue;
            }

            Player viewer = Bukkit.getPlayer(viewerUuid);

            if (viewer != null) {
                viewer.hideEntity(this.context.plugin(), indicator.display());
            }
        }

        indicator.viewers().clear();
        indicator.viewers().addAll(visible);
    }

    private boolean shouldSeeIndicator(Player speaker, Player viewer) {
        return speaker.getWorld().equals(viewer.getWorld()) &&
        viewer.canSee(speaker) &&
        this.profileService.isNametagHiddenFrom(speaker, viewer);
    }

    private Location indicatorLocation(Player player) {
        Location location = player.getEyeLocation();
        location.setY(location.getY() + HEIGHT_ABOVE_EYES);
        return location;
    }

    private void removeIndicator(UUID speakerUuid) {
        Indicator indicator = this.indicators.remove(speakerUuid);

        if (indicator != null) {
            indicator.display().remove();
        }
    }

    private record VoiceRoute(UUID speakerUuid, UUID viewerUuid) {}

    private record Indicator(TextDisplay display, Set<UUID> viewers) {}
}
