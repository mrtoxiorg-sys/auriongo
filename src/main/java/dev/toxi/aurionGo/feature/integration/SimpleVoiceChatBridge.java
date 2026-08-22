package dev.toxi.aurionGo.feature.integration;

import dev.toxi.aurionGo.config.StandardConfigs;
import dev.toxi.aurionGo.feature.player.PlayerProfileService;
import dev.toxi.aurionGo.feature.punishment.PunishmentService;
import dev.toxi.aurionGo.shared.AurionContext;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServiceRegisterEvent;

public final class SimpleVoiceChatBridge implements Listener {
    private static final String BUKKIT_SERVICE_CLASS = "de.maxhenkel.voicechat.api.BukkitVoicechatService";
    private static final String VOICECHAT_PLUGIN_CLASS = "de.maxhenkel.voicechat.api.VoicechatPlugin";
    private static final String MICROPHONE_EVENT_CLASS = "de.maxhenkel.voicechat.api.events.MicrophonePacketEvent";
    private static final String ENTITY_SOUND_EVENT_CLASS = "de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent";
    private static final long ACTIONBAR_COOLDOWN_MILLIS = 2_000L;

    private final AurionContext context;
    private final PunishmentService punishmentService;
    private final VoiceChatSpeakingIndicator speakingIndicator;
    private final ConcurrentMap<UUID, Long> lastMuteNotice = new ConcurrentHashMap<>();
    private volatile boolean active;
    private boolean hooked;
    private boolean warnedUnavailable;
    private Object proxyInstance;

    public SimpleVoiceChatBridge(
        AurionContext context,
        PunishmentService punishmentService,
        PlayerProfileService profileService
    ) {
        this.context = context;
        this.punishmentService = punishmentService;
        this.speakingIndicator = profileService == null
            ? null
            : new VoiceChatSpeakingIndicator(context, profileService);
    }

    public void enable() {
        this.active = true;
        this.context.plugin().getServer().getPluginManager().registerEvents(this, this.context.plugin());
        tryHook();
    }

    public void disable() {
        this.active = false;
        HandlerList.unregisterAll(this);

        if (this.speakingIndicator != null) {
            this.speakingIndicator.disable();
        }

        this.lastMuteNotice.clear();
        this.proxyInstance = null;
        this.hooked = false;
        this.warnedUnavailable = false;
    }

    @EventHandler
    public void onServiceRegister(ServiceRegisterEvent event) {
        if (this.hooked) {
            return;
        }

        if (BUKKIT_SERVICE_CLASS.equals(event.getProvider().getService().getName())) {
            tryHook();
        }
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (this.hooked) {
            return;
        }

        if ("voicechat".equalsIgnoreCase(event.getPlugin().getName())) {
            tryHook();
        }
    }

    private synchronized void tryHook() {
        if (!this.active || this.hooked) {
            return;
        }

        try {
            Class<?> serviceClass = Class.forName(BUKKIT_SERVICE_CLASS);
            Object service = Bukkit.getServicesManager().load(serviceClass);

            if (service == null) {
                warnUnavailableOnce("Интеграция с Simple Voice Chat ожидает регистрацию API-сервиса.");
                return;
            }

            Class<?> voicechatPluginClass = Class.forName(VOICECHAT_PLUGIN_CLASS);
            this.proxyInstance = Proxy.newProxyInstance(
                voicechatPluginClass.getClassLoader(),
                new Class[]{voicechatPluginClass},
                new VoicechatPluginHandler()
            );

            Method registerPlugin = serviceClass.getMethod("registerPlugin", voicechatPluginClass);
            registerPlugin.invoke(service, this.proxyInstance);
            this.hooked = true;

            if (this.speakingIndicator != null) {
                this.speakingIndicator.enable();
            }

            this.context.plugin().getLogger().info("Подключена интеграция с Simple Voice Chat.");
        } catch (ClassNotFoundException exception) {
            warnUnavailableOnce("Simple Voice Chat не найден в classpath сервера.");
        } catch (Exception exception) {
            handleHookFailure("Не удалось подключить Simple Voice Chat", exception);
        }
    }

    private void registerVoiceEvents(Object registration) {
        if (!this.active) {
            return;
        }

        if (this.punishmentService != null) {
            registerVoiceEvent(
                registration,
                MICROPHONE_EVENT_CLASS,
                this::handleMicrophonePacket
            );
        }

        if (this.speakingIndicator != null) {
            registerVoiceEvent(
                registration,
                ENTITY_SOUND_EVENT_CLASS,
                this::handleEntitySoundPacket
            );
        }
    }

    private void registerVoiceEvent(
        Object registration,
        String eventClassName,
        Consumer<Object> consumer
    ) {
        try {
            Class<?> eventClass = Class.forName(eventClassName);
            Method registerEvent = registration
                .getClass()
                .getMethod("registerEvent", Class.class, Consumer.class);
            registerEvent.invoke(registration, eventClass, consumer);
        } catch (Exception exception) {
            handleHookFailure("Не удалось зарегистрировать событие Simple Voice Chat " + eventClassName, exception);
        }
    }

    private void handleMicrophonePacket(Object event) {
        if (!this.active) {
            return;
        }

        try {
            UUID playerUuid = extractPlayerUuid(event);

            if (
                playerUuid == null ||
                !this.punishmentService.hasActiveMute(playerUuid)
            ) {
                return;
            }

            invokeNoArgs(event, "cancel");
            sendMuteNotice(playerUuid);
        } catch (Exception exception) {
            handleHookFailure("Ошибка при обработке голосового пакета Simple Voice Chat", exception);
        }
    }

    private void handleEntitySoundPacket(Object event) {
        if (!this.active) {
            return;
        }

        try {
            Object cancelled = invokeNoArgs(event, "isCancelled");

            if (Boolean.TRUE.equals(cancelled)) {
                return;
            }

            Object packet = invokeNoArgs(event, "getPacket");
            UUID speakerUuid = extractUuid(invokeNoArgs(packet, "getEntityUuid"));
            Object receiverConnection = invokeNoArgs(event, "getReceiverConnection");
            UUID viewerUuid = extractConnectionPlayerUuid(receiverConnection);

            if (speakerUuid != null && viewerUuid != null) {
                this.speakingIndicator.markAudible(speakerUuid, viewerUuid);
            }
        } catch (Exception exception) {
            handleHookFailure("Ошибка при обработке исходящего голосового пакета Simple Voice Chat", exception);
        }
    }

    private void sendMuteNotice(UUID playerUuid) {
        if (!shouldNotify(playerUuid)) {
            return;
        }

        Component blocked = this.punishmentService.createMuteBlockMessage(playerUuid);

        if (blocked == null) {
            return;
        }

        this.context
            .plugin()
            .getServer()
            .getScheduler()
            .runTask(this.context.plugin(), task -> {
                Player player = Bukkit.getPlayer(playerUuid);

                if (player != null) {
                    player.sendActionBar(blocked);
                }
            });
    }

    private UUID extractPlayerUuid(Object event) throws Exception {
        return extractConnectionPlayerUuid(
            invokeNoArgs(event, "getSenderConnection")
        );
    }

    private UUID extractConnectionPlayerUuid(Object connection) throws Exception {
        if (connection == null) {
            return null;
        }

        Object serverPlayer = invokeNoArgs(connection, "getPlayer");

        if (serverPlayer == null) {
            return null;
        }

        return extractUuid(invokeNoArgs(serverPlayer, "getUuid"));
    }

    private UUID extractUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }

        return value == null ? null : UUID.fromString(value.toString());
    }

    private Object invokeNoArgs(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private boolean shouldNotify(UUID playerUuid) {
        long now = System.currentTimeMillis();
        Long previous = this.lastMuteNotice.put(playerUuid, now);
        return previous == null || now - previous >= ACTIONBAR_COOLDOWN_MILLIS;
    }

    private void warnUnavailableOnce(String message) {
        if (this.warnedUnavailable) {
            return;
        }

        this.warnedUnavailable = true;

        if (!config().getBoolean("simple-voice-chat.fail-silently", true)) {
            this.context.plugin().getLogger().warning(message);
        }
    }

    private void handleHookFailure(String message, Exception exception) {
        if (config().getBoolean("simple-voice-chat.fail-silently", true)) {
            return;
        }

        this.context.plugin().getLogger().warning(message + ": " + exception.getMessage());
    }

    private FileConfiguration config() {
        return this.context.configManager().require(StandardConfigs.INTEGRATIONS).configuration();
    }

    private final class VoicechatPluginHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "getPluginId" -> "auriongo";
                case "initialize" -> null;
                case "registerEvents" -> {
                    registerVoiceEvents(args[0]);
                    yield null;
                }
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "AurionGoVoicechatPlugin";
                default -> null;
            };
        }
    }
}
