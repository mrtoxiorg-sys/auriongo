package dev.toxi.aurionGo.feature.world;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class WorldMessenger implements PluginMessageListener {

    static final String CHANNEL = "BungeeCord";

    private static final String SUBCHANNEL_GET_SERVER = "GetServer";
    private static final String SUBCHANNEL_CONNECT = "Connect";
    private static final int MAX_SERVER_NAME_LENGTH = 128;

    private final Plugin plugin;
    private final BiConsumer<Player, String> currentServerConsumer;

    public WorldMessenger(
        Plugin plugin,
        BiConsumer<Player, String> currentServerConsumer
    ) {
        this.plugin = plugin;
        this.currentServerConsumer = currentServerConsumer;
    }

    public void register() {
        this.plugin
            .getServer()
            .getMessenger()
            .registerOutgoingPluginChannel(this.plugin, CHANNEL);
        this.plugin
            .getServer()
            .getMessenger()
            .registerIncomingPluginChannel(this.plugin, CHANNEL, this);
    }

    public void unregister() {
        this.plugin
            .getServer()
            .getMessenger()
            .unregisterIncomingPluginChannel(this.plugin, CHANNEL, this);
        this.plugin
            .getServer()
            .getMessenger()
            .unregisterOutgoingPluginChannel(this.plugin, CHANNEL);
    }

    public void requestCurrentServer(Player player) {
        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        output.writeUTF(SUBCHANNEL_GET_SERVER);
        player.sendPluginMessage(this.plugin, CHANNEL, output.toByteArray());
    }

    public void connect(Player player, String server) {
        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        output.writeUTF(SUBCHANNEL_CONNECT);
        output.writeUTF(server);
        player.sendPluginMessage(this.plugin, CHANNEL, output.toByteArray());
    }

    @Override
    public void onPluginMessageReceived(
        String channel,
        Player player,
        byte[] message
    ) {
        if (!CHANNEL.equals(channel)) {
            return;
        }

        String server = readServerName(message);

        if (server == null) {
            return;
        }

        this.currentServerConsumer.accept(player, server);
    }

    private String readServerName(byte[] message) {
        try (
            DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(message)
            )
        ) {
            if (!SUBCHANNEL_GET_SERVER.equals(input.readUTF())) {
                return null;
            }

            String server = input.readUTF();

            if (server.isBlank() || server.length() > MAX_SERVER_NAME_LENGTH) {
                return null;
            }

            return sanitize(server);
        } catch (IOException exception) {
            return null;
        }
    }

    private String sanitize(String server) {
        String trimmed = server.trim();
        byte[] bytes = trimmed.getBytes(StandardCharsets.UTF_8);

        for (byte value : bytes) {
            if (value < 0x20) {
                return null;
            }
        }

        return trimmed;
    }
}
