package dev.toxi.aurionGo.feature.integration;

import dev.toxi.aurionGo.config.StandardConfigs;
import dev.toxi.aurionGo.feature.punishment.PunishmentService;
import dev.toxi.aurionGo.shared.AurionContext;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServiceRegisterEvent;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

public final class SimpleVoiceChatBridge implements Listener {
    private static final String BUKKIT_SERVICE_CLASS = "de.maxhenkel.voicechat.api.BukkitVoicechatService";
    private static final String VOICECHAT_PLUGIN_CLASS = "de.maxhenkel.voicechat.api.VoicechatPlugin";
    private static final String MICROPHONE_EVENT_CLASS = "de.maxhenkel.voicechat.api.events.MicrophonePacketEvent";
    private static final long ACTIONBAR_COOLDOWN_MILLIS = 2_000L;

    private final AurionContext context;
    private final PunishmentService punishmentService;
    private final ConcurrentMap<UUID, Long> lastMuteNotice = new ConcurrentHashMap<>();
    private boolean hooked;
    private boolean warnedUnavailable;
    private Object proxyInstance;

    public SimpleVoiceChatBridge(AurionContext context, PunishmentService punishmentService) {
        this.context = context;
        this.punishmentService = punishmentService;
    }

    public void enable() {
        this.context.plugin().getServer().getPluginManager().registerEvents(this, this.context.plugin());
        tryHook();
    }

    public void disable() {
        HandlerList.unregisterAll(this);
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
        if (this.hooked) {
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
            this.context.plugin().getLogger().info("Подключена интеграция с Simple Voice Chat.");
        } catch (ClassNotFoundException exception) {
            warnUnavailableOnce("Simple Voice Chat не найден в classpath сервера.");
        } catch (Exception exception) {
            handleHookFailure("Не удалось подключить Simple Voice Chat", exception);
        }
    }

    private void registerVoiceEvents(Object registration) {
        try {
            Class<?> microphoneEventClass = Class.forName(MICROPHONE_EVENT_CLASS);
            Method registerEvent = registration.getClass().getMethod("registerEvent", Class.class, Consumer.class);
            registerEvent.invoke(registration, microphoneEventClass, (Consumer<Object>) this::handleMicrophonePacket);
        } catch (Exception exception) {
            handleHookFailure("Не удалось зарегистрировать события Simple Voice Chat", exception);
        }
    }

    private void handleMicrophonePacket(Object event) {
        try {
            UUID playerUuid = extractPlayerUuid(event);

            if (playerUuid == null || !this.punishmentService.hasActiveMute(playerUuid)) {
                return;
            }

            invokeNoArgs(event, "cancel");

            Player player = Bukkit.getPlayer(playerUuid);

            if (player == null || !shouldNotify(playerUuid)) {
                return;
            }

            Component blocked = this.punishmentService.createMuteBlockMessage(playerUuid);

            if (blocked != null) {
                this.punishmentService.sendMuteBlockMessage(player, blocked);
            }
        } catch (Exception exception) {
            handleHookFailure("Ошибка при обработке голосового пакета Simple Voice Chat", exception);
        }
    }

    private UUID extractPlayerUuid(Object event) throws Exception {
        Object senderConnection = invokeNoArgs(event, "getSenderConnection");

        if (senderConnection == null) {
            return null;
        }

        Object serverPlayer = invokeNoArgs(senderConnection, "getPlayer");

        if (serverPlayer == null) {
            return null;
        }

        Object uuid = invokeNoArgs(serverPlayer, "getUuid");

        if (uuid instanceof UUID playerUuid) {
            return playerUuid;
        }

        return uuid == null ? null : UUID.fromString(uuid.toString());
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
