package dev.toxi.aurionGo.feature.spectral;

import java.util.List;
import java.util.Locale;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public record SpectralSettings(
    boolean brewingEnabled,
    Material murkyIngredient,
    Material spectralIngredient,
    boolean lingeringEnabled,
    Material lingeringIngredient,
    int minGlowTicks,
    int maxGlowTicks,
    boolean onlyInvisible,
    boolean affectMobs,
    Appearance murky,
    Appearance spectral
) {
    private static final int MIN_ALLOWED_SECONDS = 1;
    private static final int MAX_ALLOWED_SECONDS = 3600;
    private static final int TICKS_PER_SECOND = 20;

    public record Appearance(
        String name,
        Color color,
        List<String> lore,
        boolean glint
    ) {}

    public int nominalGlowTicks() {
        return (this.minGlowTicks + this.maxGlowTicks) / 2;
    }

    public static SpectralSettings from(FileConfiguration configuration) {
        int minSeconds = clampSeconds(configuration.getInt("glowing.min-seconds", 30));
        int maxSeconds = clampSeconds(configuration.getInt("glowing.max-seconds", 60));
        int lowSeconds = Math.min(minSeconds, maxSeconds);
        int highSeconds = Math.max(minSeconds, maxSeconds);

        return new SpectralSettings(
            configuration.getBoolean("brewing.enabled", true),
            material(configuration.getString("brewing.murky-ingredient"), Material.BONE_MEAL),
            material(configuration.getString("brewing.spectral-ingredient"), Material.GLOWSTONE_DUST),
            configuration.getBoolean("brewing.lingering-enabled", true),
            material(configuration.getString("brewing.lingering-ingredient"), Material.DRAGON_BREATH),
            lowSeconds * TICKS_PER_SECOND,
            highSeconds * TICKS_PER_SECOND,
            configuration.getBoolean("glowing.only-invisible", true),
            configuration.getBoolean("glowing.affect-mobs", false),
            appearance(
                configuration.getConfigurationSection("items.murky"),
                "<gray>Мутноватое зелье</gray>",
                Color.fromRGB(0xC9C9C4),
                false
            ),
            appearance(
                configuration.getConfigurationSection("items.spectral"),
                "<gradient:#FFE259:#F2A100>Спектральное зелье</gradient>",
                Color.fromRGB(0xF2C200),
                true
            )
        );
    }

    private static int clampSeconds(int seconds) {
        return Math.max(MIN_ALLOWED_SECONDS, Math.min(MAX_ALLOWED_SECONDS, seconds));
    }

    private static Material material(String rawName, Material fallback) {
        if (rawName == null || rawName.isBlank()) {
            return fallback;
        }

        Material parsed = Material.matchMaterial(rawName.trim().toUpperCase(Locale.ROOT));

        if (parsed == null || parsed.isAir() || !parsed.isItem()) {
            return fallback;
        }

        return parsed;
    }

    private static Appearance appearance(
        ConfigurationSection section,
        String fallbackName,
        Color fallbackColor,
        boolean fallbackGlint
    ) {
        if (section == null) {
            return new Appearance(fallbackName, fallbackColor, List.of(), fallbackGlint);
        }

        return new Appearance(
            section.getString("name", fallbackName),
            color(section.getString("color"), fallbackColor),
            List.copyOf(section.getStringList("lore")),
            section.getBoolean("glint", fallbackGlint)
        );
    }

    private static Color color(String rawColor, Color fallback) {
        if (rawColor == null || rawColor.isBlank()) {
            return fallback;
        }

        String normalized = rawColor.trim();

        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }

        try {
            return Color.fromRGB(Integer.parseInt(normalized, 16) & 0xFFFFFF);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
