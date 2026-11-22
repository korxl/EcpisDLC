package dev.ecpis.ecpisdlc;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class OverlayPreferences {
    int width = 1020;
    int height = 680;
    String tab = "combat";
    String theme = "obsidian-violet";
    String activeConfig = "default";
    final Map<String, Boolean> enabled = new LinkedHashMap<>();
    final Map<String, String> binds = new LinkedHashMap<>();
    final Map<String, String> settings = new LinkedHashMap<>();
    final Map<String, String> configs = new LinkedHashMap<>();

    private static final Pattern STRING_FIELD = Pattern.compile("\\\"((?:\\\\.|[^\\\"])*)\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
    private static final Pattern BOOLEAN_FIELD = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*(true|false)");

    record Profile(LinkedHashMap<String, Boolean> enabled, LinkedHashMap<String, String> settings,
                   LinkedHashMap<String, String> binds) { }

    private OverlayPreferences() { }

    static OverlayPreferences load() {
        OverlayPreferences result = new OverlayPreferences();
        Path file = configPath();
        if (!Files.isRegularFile(file)) return result;
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            result.width = readInt(json, "width", result.width);
            result.height = readInt(json, "height", result.height);
            result.tab = readString(json, "tab", result.tab);
            result.theme = readString(json, "theme", result.theme);
            result.activeConfig = readString(json, "activeConfig", result.activeConfig);
            Matcher enabledMatcher = BOOLEAN_FIELD.matcher(objectBody(json, "enabled"));
            while (enabledMatcher.find()) result.enabled.put(enabledMatcher.group(1), Boolean.parseBoolean(enabledMatcher.group(2)));
            readStringMap(objectBody(json, "binds"), result.binds);
            readStringMap(objectBody(json, "settings"), result.settings);
            readStringMap(objectBody(json, "configs"), result.configs);
        } catch (IOException ignored) {
            // Invalid or unavailable preferences fall back to defaults.
        }
        return result;
    }

    void save() {
        Path file = configPath();
        StringBuilder json = new StringBuilder("{\n  \"width\": ").append(width)
                .append(",\n  \"height\": ").append(height)
                .append(",\n  \"tab\": \"").append(escape(tab)).append("\",\n  \"theme\": \"").append(escape(theme))
                .append("\",\n  \"activeConfig\": \"").append(escape(activeConfig)).append("\",\n  \"enabled\": {");
        appendBooleanMap(json, enabled);
        json.append("\n  },\n  \"binds\": {");
        appendStringMap(json, binds);
        json.append("\n  },\n  \"settings\": {");
        appendStringMap(json, settings);
        json.append("\n  },\n  \"configs\": {");
        appendStringMap(json, configs);
        json.append("\n  }\n}\n");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, json, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // UI state remains usable for this session if preferences cannot be written.
        }
    }

    static String encodeProfile(Map<String, Boolean> enabled, Map<String, String> settings, Map<String, String> binds) {
        StringBuilder packed = new StringBuilder("v2");
        appendPackedMap(packed, 'e', enabled);
        appendPackedMap(packed, 's', settings);
        appendPackedMap(packed, 'b', binds);
        return packed.toString();
    }

    static Profile decodeProfile(String packed) {
        LinkedHashMap<String, Boolean> enabled = new LinkedHashMap<>();
        LinkedHashMap<String, String> settings = new LinkedHashMap<>();
        LinkedHashMap<String, String> binds = new LinkedHashMap<>();
        if (packed.startsWith("v2")) {
            for (String entry : packed.split("\\|")) {
                String[] parts = entry.split(":", 3);
                if (parts.length != 3 || parts[0].length() != 1) continue;
                String key;
                String value;
                try {
                    key = decode(parts[1]);
                    value = decode(parts[2]);
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                switch (parts[0].charAt(0)) {
                    case 'e' -> enabled.put(key, Boolean.parseBoolean(value));
                    case 's' -> settings.put(key, value);
                    case 'b' -> binds.put(key, value);
                    default -> { }
                }
            }
        } else {
            for (String pair : packed.split(",")) {
                String[] parts = pair.split("=", 2);
                if (parts.length == 2) enabled.put(parts[0], "1".equals(parts[1]) || "true".equals(parts[1]));
            }
        }
        return new Profile(enabled, settings, binds);
    }

    private static void appendPackedMap(StringBuilder packed, char section, Map<String, ?> values) {
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            String value = String.valueOf(entry.getValue());
            packed.append('|').append(section).append(':').append(encode(entry.getKey())).append(':').append(encode(value));
        }
    }

    static double checkboxValue(String stored) {
        if ("true".equalsIgnoreCase(stored)) return 1.0;
        if ("false".equalsIgnoreCase(stored)) return 0.0;
        return Double.parseDouble(stored) != 0.0 ? 1.0 : 0.0;
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static void readStringMap(String body, Map<String, String> destination) {
        Matcher matcher = STRING_FIELD.matcher(body);
        while (matcher.find()) destination.put(unescape(matcher.group(1)), unescape(matcher.group(2)));
    }

    private static void appendBooleanMap(StringBuilder json, Map<String, Boolean> values) {
        int index = 0;
        for (Map.Entry<String, Boolean> entry : values.entrySet()) {
            if (index++ > 0) json.append(',');
            json.append("\n    \"").append(escape(entry.getKey())).append("\": ").append(entry.getValue());
        }
        if (!values.isEmpty()) json.append('\n').append("  ");
    }

    private static void appendStringMap(StringBuilder json, Map<String, String> values) {
        int index = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (index++ > 0) json.append(',');
            json.append("\n    \"").append(escape(entry.getKey())).append("\": \"").append(escape(entry.getValue())).append('\"');
        }
        if (!values.isEmpty()) json.append('\n').append("  ");
    }

    private static int readInt(String json, String key, int fallback) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*(\\d+)").matcher(json);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : fallback;
    }

    private static String readString(String json, String key, String fallback) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"").matcher(json);
        return matcher.find() ? unescape(matcher.group(1)) : fallback;
    }

    private static String objectBody(String json, String key) {
        Matcher start = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\{").matcher(json);
        if (!start.find()) return "";
        int depth = 1;
        boolean quoted = false;
        boolean escaped = false;
        for (int index = start.end(); index < json.length(); index++) {
            char character = json.charAt(index);
            if (escaped) { escaped = false; continue; }
            if (character == '\\' && quoted) { escaped = true; continue; }
            if (character == '\"') { quoted = !quoted; continue; }
            if (!quoted) {
                if (character == '{') depth++;
                else if (character == '}' && --depth == 0) return json.substring(start.end(), index);
            }
        }
        return "";
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("ecpisdlc/ui.json");
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unescape(String value) {
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
