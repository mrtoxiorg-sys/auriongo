package dev.toxi.aurionGo.feature.spectral;

import dev.toxi.aurionGo.config.StandardConfigs;
import dev.toxi.aurionGo.module.PluginModule;
import dev.toxi.aurionGo.shared.AurionContext;
import org.bukkit.event.HandlerList;

public final class SpectralModule implements PluginModule {

    private final AurionContext context;
    private SpectralBrewingService brewingService;
    private SpectralPotionListener listener;

    public SpectralModule(AurionContext context) {
        this.context = context;
    }

    @Override
    public String id() {
        return "spectral";
    }

    @Override
    public void enable() {
        SpectralSettings settings = SpectralSettings.from(
            this.context.configManager().require(StandardConfigs.SPECTRAL).configuration()
        );
        SpectralItems items = new SpectralItems(this.context.plugin(), settings);

        this.brewingService = new SpectralBrewingService(
            this.context.plugin(),
            items,
            settings
        );
        this.brewingService.register();

        this.listener = new SpectralPotionListener(
            this.context.plugin(),
            items,
            settings
        );
        this.context
            .plugin()
            .getServer()
            .getPluginManager()
            .registerEvents(this.listener, this.context.plugin());
    }

    @Override
    public void disable() {
        if (this.listener != null) {
            HandlerList.unregisterAll(this.listener);
            this.listener = null;
        }

        if (this.brewingService != null) {
            this.brewingService.unregister();
            this.brewingService = null;
        }
    }
}
