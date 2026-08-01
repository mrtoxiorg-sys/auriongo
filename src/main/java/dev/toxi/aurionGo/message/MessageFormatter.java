package dev.toxi.aurionGo.message;

import dev.toxi.aurionGo.config.ConfigFile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MessageFormatter {
    private static final Pattern FEATURE_COLOR_PATTERN = Pattern.compile("<(/?)fcolor(?::([a-zA-Z0-9_-]+))?>");
    private static final String DEFAULT_FEATURE_COLOR = "color:#4FD6FF";

    private final ConfigFile coreConfig;
    private final MessageService messageService;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public MessageFormatter(ConfigFile coreConfig, MessageService messageService) {
        this.coreConfig = coreConfig;
        this.messageService = messageService;
    }

    public String get(String path) {
        return applyFeatureColors(this.messageService.get(path));
    }

    public String getOrDefault(String path, String fallback) {
        return applyFeatureColors(this.messageService.getOrDefault(path, fallback));
    }

    public Component render(String path, Map<String, String> placeholders) {
        return renderRaw(
                getOrDefault(path, "<color:#FF4F4F>В конфиге отсутствует сообщение: " + path),
                placeholders
        );
    }

    public Component renderRaw(String template, Map<String, String> placeholders) {
        if (template == null || template.isEmpty()) {
            return Component.empty();
        }

        String resolved = applyFeatureColors(template);

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolved = resolved.replace("{" + entry.getKey() + "}", this.miniMessage.escapeTags(entry.getValue()));
        }

        return this.miniMessage.deserialize(resolved);
    }

    public String resolveTemplate(String template) {
        return applyFeatureColors(template);
    }

    public String escapeTags(String input) {
        return this.miniMessage.escapeTags(input);
    }

    private String applyFeatureColors(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        Matcher matcher = FEATURE_COLOR_PATTERN.matcher(input);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            boolean closing = "/".equals(matcher.group(1));
            String colorKey = matcher.group(2) == null ? "1" : matcher.group(2);
            String specification = featureColor(colorKey);
            String replacement = closing
                    ? "</" + tagName(specification) + ">"
                    : "<" + normalizeSpecification(specification) + ">";
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String featureColor(String key) {
        String path = "theme.fcolors." + key;
        String fallback = this.coreConfig.configuration().getString("theme.fcolors.1", DEFAULT_FEATURE_COLOR);
        return this.coreConfig.configuration().getString(path, fallback == null ? DEFAULT_FEATURE_COLOR : fallback);
    }

    private String normalizeSpecification(String specification) {
        String normalized = specification == null ? DEFAULT_FEATURE_COLOR : specification.trim();

        if (normalized.startsWith("<") && normalized.endsWith(">") && normalized.length() > 2) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }

        return normalized.isEmpty() ? DEFAULT_FEATURE_COLOR : normalized;
    }

    private String tagName(String specification) {
        String normalized = normalizeSpecification(specification);
        int separatorIndex = normalized.indexOf(':');

        if (separatorIndex < 0) {
            separatorIndex = normalized.indexOf(' ');
        }

        return separatorIndex < 0 ? normalized : normalized.substring(0, separatorIndex);
    }
}
