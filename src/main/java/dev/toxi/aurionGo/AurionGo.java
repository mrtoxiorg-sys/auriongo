package dev.toxi.aurionGo;

import dev.toxi.aurionGo.bootstrap.AurionBootstrap;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class AurionGo extends JavaPlugin {
    private AurionBootstrap bootstrap;

    @Override
    public void onEnable() {
        this.bootstrap = new AurionBootstrap(this);

        try {
            this.bootstrap.enable();
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Не удалось включить AurionGo.", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (this.bootstrap != null) {
            this.bootstrap.disable();
        }
    }
}
