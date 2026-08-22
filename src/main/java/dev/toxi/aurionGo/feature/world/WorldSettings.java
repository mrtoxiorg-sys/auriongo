package dev.toxi.aurionGo.feature.world;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public record WorldSettings(
    List<String> servers,
    Map<String, String> displayNames,
    long cooldownMillis,
    long combatBlockMillis,
    long connectDelayTicks,
    long lookupTimeoutMillis
) {
    private static final long MILLIS_PER_SECOND = 1000L;

    public boolean isValid() {
        return this.servers.size() >= 2;
    }

    public String displayName(String server) {
        if (server == null) {
            return "";
        }

        return this.displayNames.getOrDefault(
            server.toLowerCase(Locale.ROOT),
            server
        );
    }

    public boolean contains(String server) {
        return indexOf(server) >= 0;
    }

    public String nextServer(String currentServer) {
        int index = indexOf(currentServer);

        if (index < 0) {
            return null;
        }

        return this.servers.get((index + 1) % this.servers.size());
    }

    private int indexOf(String server) {
        if (server == null) {
            return -1;
        }

        for (int index = 0; index < this.servers.size(); index++) {
            if (this.servers.get(index).equalsIgnoreCase(server)) {
                return index;
            }
        }

        return -1;
    }

    public static WorldSettings from(FileConfiguration configuration) {
        return new WorldSettings(
            servers(configuration.getStringList("servers")),
            displayNames(configuration.getConfigurationSection("display-names")),
            Math.max(0L, configuration.getLong("cooldown-seconds", 10L)) * MILLIS_PER_SECOND,
            Math.max(0L, configuration.getLong("combat-block-seconds", 15L)) * MILLIS_PER_SECOND,
            Math.max(0L, configuration.getLong("connect-delay-ticks", 10L)),
            Math.max(500L, configuration.getLong("lookup-timeout-millis", 5000L))
        );
    }

    private static List<String> servers(List<String> rawServers) {
        List<String> servers = new ArrayList<>(rawServers.size());

        for (String rawServer : rawServers) {
            if (rawServer == null || rawServer.isBlank()) {
                continue;
            }

            String server = rawServer.trim();

            if (!containsIgnoreCase(servers, server)) {
                servers.add(server);
            }
        }

        return List.copyOf(servers);
    }

    private static boolean containsIgnoreCase(List<String> servers, String candidate) {
        for (String server : servers) {
            if (server.equalsIgnoreCase(candidate)) {
                return true;
            }
        }

        return false;
    }

    private static Map<String, String> displayNames(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }

        Map<String, String> displayNames = new LinkedHashMap<>();

        for (String key : section.getKeys(false)) {
            String value = section.getString(key);

            if (value != null && !value.isBlank()) {
                displayNames.put(key.toLowerCase(Locale.ROOT), value);
            }
        }

        return Map.copyOf(displayNames);
    }
}
