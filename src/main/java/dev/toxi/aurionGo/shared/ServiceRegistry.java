package dev.toxi.aurionGo.shared;

import java.util.HashMap;
import java.util.Map;

public final class ServiceRegistry {
    private final Map<Class<?>, Object> services = new HashMap<>();

    public <T> void register(Class<T> type, T service) {
        this.services.put(type, service);
    }

    public <T> T require(Class<T> type) {
        Object service = this.services.get(type);

        if (service == null) {
            throw new IllegalStateException("Missing service registration: " + type.getName());
        }

        return type.cast(service);
    }
}
