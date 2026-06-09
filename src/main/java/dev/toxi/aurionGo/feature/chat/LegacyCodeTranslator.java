package dev.toxi.aurionGo.feature.chat;

import java.util.HashMap;
import java.util.Map;

public final class LegacyCodeTranslator {
    private static final Map<Character, String> MINI_MESSAGE_CODES = new HashMap<>();

    static {
        MINI_MESSAGE_CODES.put('0', "<black>");
        MINI_MESSAGE_CODES.put('1', "<dark_blue>");
        MINI_MESSAGE_CODES.put('2', "<dark_green>");
        MINI_MESSAGE_CODES.put('3', "<dark_aqua>");
        MINI_MESSAGE_CODES.put('4', "<dark_red>");
        MINI_MESSAGE_CODES.put('5', "<dark_purple>");
        MINI_MESSAGE_CODES.put('6', "<gold>");
        MINI_MESSAGE_CODES.put('7', "<gray>");
        MINI_MESSAGE_CODES.put('8', "<dark_gray>");
        MINI_MESSAGE_CODES.put('9', "<blue>");
        MINI_MESSAGE_CODES.put('a', "<green>");
        MINI_MESSAGE_CODES.put('b', "<aqua>");
        MINI_MESSAGE_CODES.put('c', "<red>");
        MINI_MESSAGE_CODES.put('d', "<light_purple>");
        MINI_MESSAGE_CODES.put('e', "<yellow>");
        MINI_MESSAGE_CODES.put('f', "<white>");
        MINI_MESSAGE_CODES.put('k', "<obfuscated>");
        MINI_MESSAGE_CODES.put('l', "<bold>");
        MINI_MESSAGE_CODES.put('m', "<strikethrough>");
        MINI_MESSAGE_CODES.put('n', "<underlined>");
        MINI_MESSAGE_CODES.put('o', "<italic>");
        MINI_MESSAGE_CODES.put('r', "<reset>");
    }

    private LegacyCodeTranslator() {
    }

    public static String toMiniMessage(String input, boolean allowAmpersand, boolean allowSection) {
        StringBuilder builder = new StringBuilder();

        for (int index = 0; index < input.length(); index++) {
            char current = input.charAt(index);

            if (!isLegacyMarker(current, allowAmpersand, allowSection) || index + 1 >= input.length()) {
                builder.append(current);
                continue;
            }

            String hexTag = readHexTag(input, index, current);

            if (hexTag != null) {
                builder.append(hexTag);
                index += hexTag.length() == 9 ? 7 : 13;
                continue;
            }

            char code = Character.toLowerCase(input.charAt(index + 1));
            String replacement = MINI_MESSAGE_CODES.get(code);

            if (replacement == null) {
                builder.append(current);
                continue;
            }

            builder.append(replacement);
            index++;
        }

        return builder.toString();
    }

    public static String toSectionCodes(String input, boolean allowAmpersand, boolean allowSection) {
        if (allowSection) {
            return input;
        }

        if (!allowAmpersand) {
            return input;
        }

        return input.replace('&', '§');
    }

    private static boolean isLegacyMarker(char character, boolean allowAmpersand, boolean allowSection) {
        return (character == '&' && allowAmpersand) || (character == '§' && allowSection);
    }

    private static String readHexTag(String input, int index, char marker) {
        if (index + 7 < input.length() && input.charAt(index + 1) == '#') {
            String hex = input.substring(index + 2, index + 8);

            if (hex.matches("[0-9a-fA-F]{6}")) {
                return "<#" + hex + ">";
            }
        }

        if (index + 13 < input.length() && Character.toLowerCase(input.charAt(index + 1)) == 'x') {
            StringBuilder hex = new StringBuilder();

            for (int offset = 2; offset <= 12; offset += 2) {
                if (input.charAt(index + offset) != marker) {
                    return null;
                }

                char hexChar = input.charAt(index + offset + 1);

                if (!isHex(hexChar)) {
                    return null;
                }

                hex.append(hexChar);
            }

            return "<#" + hex + ">";
        }

        return null;
    }

    private static boolean isHex(char character) {
        return (character >= '0' && character <= '9')
                || (character >= 'a' && character <= 'f')
                || (character >= 'A' && character <= 'F');
    }
}
