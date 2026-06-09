package dev.toxi.aurionGo.message;

public interface MessageService {
    String get(String path);

    String getOrDefault(String path, String fallback);
}
