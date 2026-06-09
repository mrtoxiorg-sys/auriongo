package dev.toxi.aurionGo.feature.punishment;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {
    private static final Pattern PART_PATTERN = Pattern.compile("(\\d+)([smhdw])", Pattern.CASE_INSENSITIVE);

    private DurationParser() {
    }

    public static Long parseToMillis(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        String normalized = input.trim().toLowerCase();

        if (normalized.equals("perm") || normalized.equals("permanent") || normalized.equals("навсегда")) {
            return null;
        }

        Matcher matcher = PART_PATTERN.matcher(normalized);
        long totalMillis = 0L;
        int consumed = 0;

        while (matcher.find()) {
            consumed += matcher.group(0).length();
            long amount = Long.parseLong(matcher.group(1));
            char unit = matcher.group(2).charAt(0);
            totalMillis += switch (unit) {
                case 's' -> amount * 1000L;
                case 'm' -> amount * 60_000L;
                case 'h' -> amount * 3_600_000L;
                case 'd' -> amount * 86_400_000L;
                case 'w' -> amount * 604_800_000L;
                default -> 0L;
            };
        }

        if (consumed != normalized.length() || totalMillis <= 0L) {
            return null;
        }

        return totalMillis;
    }
}
