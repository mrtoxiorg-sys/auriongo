package dev.toxi.aurionGo.module;

import dev.toxi.aurionGo.config.ConfigFile;
import dev.toxi.aurionGo.config.StandardConfigs;
import dev.toxi.aurionGo.shared.AurionContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModuleManager {
    private final AurionContext context;
    private final List<PluginModule> registeredModules = new ArrayList<>();
    private final List<PluginModule> activeModules = new ArrayList<>();

    public ModuleManager(AurionContext context) {
        this.context = context;
    }

    public void register(PluginModule module) {
        this.registeredModules.add(module);
    }

    public void enableAll() {
        ConfigFile coreConfig = this.context.configManager().require(StandardConfigs.CORE);

        for (PluginModule module : this.registeredModules) {
            boolean enabled = coreConfig.configuration().getBoolean("modules." + module.id() + ".enabled", true);

            if (!enabled) {
                continue;
            }

            module.enable();
            this.activeModules.add(module);
        }
    }

    public void disableAll() {
        List<PluginModule> modules = new ArrayList<>(this.activeModules);
        Collections.reverse(modules);

        for (PluginModule module : modules) {
            module.disable();
        }

        this.activeModules.clear();
    }
}
