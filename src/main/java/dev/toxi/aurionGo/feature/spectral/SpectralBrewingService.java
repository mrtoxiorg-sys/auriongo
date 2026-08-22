package dev.toxi.aurionGo.feature.spectral;

import io.papermc.paper.potion.PotionMix;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.plugin.Plugin;

public final class SpectralBrewingService {

    private static final String MIX_MURKY = "murky_potion";
    private static final String MIX_SPECTRAL = "spectral_potion";
    private static final String MIX_SPECTRAL_LINGERING = "spectral_potion_lingering";

    private final Plugin plugin;
    private final SpectralItems items;
    private final SpectralSettings settings;
    private final List<NamespacedKey> registeredKeys = new ArrayList<>();

    public SpectralBrewingService(
        Plugin plugin,
        SpectralItems items,
        SpectralSettings settings
    ) {
        this.plugin = plugin;
        this.items = items;
        this.settings = settings;
    }

    public void register() {
        unregister();

        if (!this.settings.brewingEnabled()) {
            return;
        }

        addMix(
            MIX_MURKY,
            this.items.createMurky(),
            PotionMix.createPredicateChoice(this.items::isPlainWaterBottle),
            new RecipeChoice.MaterialChoice(this.settings.murkyIngredient())
        );
        addMix(
            MIX_SPECTRAL,
            this.items.createSpectralSplash(),
            PotionMix.createPredicateChoice(this.items::isMurky),
            new RecipeChoice.MaterialChoice(this.settings.spectralIngredient())
        );

        if (!this.settings.lingeringEnabled()) {
            return;
        }

        addMix(
            MIX_SPECTRAL_LINGERING,
            this.items.createSpectralLingering(),
            PotionMix.createPredicateChoice(this.items::isSpectralSplash),
            new RecipeChoice.MaterialChoice(this.settings.lingeringIngredient())
        );
    }

    public void unregister() {
        List<NamespacedKey> keys = this.registeredKeys.isEmpty()
            ? knownKeys()
            : new ArrayList<>(this.registeredKeys);

        for (NamespacedKey key : keys) {
            removeMix(key);
        }

        this.registeredKeys.clear();
    }

    private List<NamespacedKey> knownKeys() {
        return List.of(
            new NamespacedKey(this.plugin, MIX_MURKY),
            new NamespacedKey(this.plugin, MIX_SPECTRAL),
            new NamespacedKey(this.plugin, MIX_SPECTRAL_LINGERING)
        );
    }

    private void addMix(
        String id,
        ItemStack result,
        RecipeChoice input,
        RecipeChoice ingredient
    ) {
        NamespacedKey key = new NamespacedKey(this.plugin, id);

        try {
            Bukkit.getPotionBrewer().addPotionMix(
                new PotionMix(key, result, input, ingredient)
            );
            this.registeredKeys.add(key);
        } catch (Exception exception) {
            this.plugin
                .getLogger()
                .warning(
                    "Не удалось зарегистрировать рецепт зельеварки " +
                    key +
                    ": " +
                    exception.getMessage()
                );
        }
    }

    private void removeMix(NamespacedKey key) {
        try {
            Bukkit.getPotionBrewer().removePotionMix(key);
        } catch (Exception exception) {
            this.plugin
                .getLogger()
                .warning(
                    "Не удалось снять рецепт зельеварки " +
                    key +
                    ": " +
                    exception.getMessage()
                );
        }
    }
}
