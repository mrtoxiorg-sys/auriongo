package dev.toxi.aurionGo.module;

public interface PluginModule {
    String id();

    void enable();

    void disable();
}
