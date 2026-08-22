package dev.toxi.aurionGo.feature.spectral;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

public final class SpectralItems {

    public static final String KIND_MURKY = "murky";
    public static final String KIND_SPECTRAL = "spectral";

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final NamespacedKey kindKey;
    private final SpectralSettings settings;

    public SpectralItems(Plugin plugin, SpectralSettings settings) {
        this.kindKey = new NamespacedKey(plugin, "spectral_kind");
        this.settings = settings;
    }

    public NamespacedKey kindKey() {
        return this.kindKey;
    }

    public String kindOf(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) {
            return null;
        }

        return item
            .getPersistentDataContainer()
            .get(this.kindKey, PersistentDataType.STRING);
    }

    public boolean isPlainWaterBottle(ItemStack item) {
        if (item == null || item.getType() != Material.POTION) {
            return false;
        }

        if (kindOf(item) != null) {
            return false;
        }

        if (!(item.getItemMeta() instanceof PotionMeta meta)) {
            return false;
        }

        if (meta.hasCustomEffects()) {
            return false;
        }

        PotionType base = meta.getBasePotionType();
        return base == null || base == PotionType.WATER;
    }

    public boolean isMurky(ItemStack item) {
        return item != null &&
            item.getType() == Material.POTION &&
            KIND_MURKY.equals(kindOf(item));
    }

    public boolean isSpectral(ItemStack item) {
        return item != null && KIND_SPECTRAL.equals(kindOf(item));
    }

    public boolean isSpectralSplash(ItemStack item) {
        return item != null &&
            item.getType() == Material.SPLASH_POTION &&
            KIND_SPECTRAL.equals(kindOf(item));
    }

    public boolean isSpectral(ThrownPotion potion) {
        return isSpectral(potion.getItem());
    }

    public ItemStack createMurky() {
        return create(Material.POTION, KIND_MURKY, this.settings.murky(), false);
    }

    public ItemStack createSpectralSplash() {
        return create(
            Material.SPLASH_POTION,
            KIND_SPECTRAL,
            this.settings.spectral(),
            true
        );
    }

    public ItemStack createSpectralLingering() {
        return create(
            Material.LINGERING_POTION,
            KIND_SPECTRAL,
            this.settings.spectral(),
            true
        );
    }

    private ItemStack create(
        Material material,
        String kind,
        SpectralSettings.Appearance appearance,
        boolean glowingEffect
    ) {
        ItemStack item = ItemStack.of(material);
        item.editMeta(PotionMeta.class, meta -> applyMeta(meta, appearance, glowingEffect));
        item.editPersistentDataContainer(container ->
            container.set(this.kindKey, PersistentDataType.STRING, kind)
        );
        return item;
    }

    private void applyMeta(
        PotionMeta meta,
        SpectralSettings.Appearance appearance,
        boolean glowingEffect
    ) {
        meta.setBasePotionType(PotionType.MUNDANE);
        meta.clearCustomEffects();

        if (glowingEffect) {
            meta.addCustomEffect(
                new PotionEffect(
                    PotionEffectType.GLOWING,
                    this.settings.nominalGlowTicks(),
                    0,
                    false,
                    true,
                    true
                ),
                true
            );
        }

        meta.setColor(appearance.color());
        meta.itemName(render(appearance.name()));
        meta.lore(renderLore(appearance.lore()));
        meta.setEnchantmentGlintOverride(appearance.glint() ? Boolean.TRUE : null);

        if (!glowingEffect) {
            meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        }
    }

    private List<Component> renderLore(List<String> rawLore) {
        List<Component> lore = new ArrayList<>(rawLore.size());

        for (String line : rawLore) {
            lore.add(render(line));
        }

        return lore;
    }

    private Component render(String raw) {
        return MINI_MESSAGE
            .deserialize(raw)
            .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
