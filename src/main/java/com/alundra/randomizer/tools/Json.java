package com.alundra.randomizer.tools;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON helpers.
 * <p>
 * Reading is done by {@link JsonParser} (comments allowed, object key order preserved). Output documents are built as
 * plain {@link LinkedHashMap} / {@link List} trees (insertion order is kept) and serialized by {@link #dump(Object)},
 * which produces the same layout as the original randomizer's output (4-space indentation, {@code "key": value}).
 */
public final class Json {
    private Json() {
    }

    public static JsonNode parse(String text) {
        return JsonParser.parse(text);
    }

    public static JsonNode parseResource(String resourcePath) {
        try (InputStream stream = Json.class.getResourceAsStream(resourcePath)) {
            if (stream == null)
                throw new RandomizerException("Missing resource '" + resourcePath + "'");
            return parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RandomizerException("Could not read resource '" + resourcePath + "'", e);
        }
    }

    /** @return the (key, value) pairs of a JSON object, in document order */
    public static Iterable<Map.Entry<String, JsonNode>> fields(JsonNode node) {
        return node::fields;
    }

    public static List<String> stringList(JsonNode node) {
        List<String> list = new ArrayList<>();
        for (JsonNode element : node)
            list.add(element.asText());
        return list;
    }

    /** Creates an empty, insertion-ordered JSON object. */
    public static Map<String, Object> object() {
        return new LinkedHashMap<>();
    }

    /**
     * Gets the child object at the given key, creating it if needed (same as {@code json["key"]} on a null/object
     * value in nlohmann::json).
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> child(Map<String, Object> parent, String key) {
        return (Map<String, Object>) parent.computeIfAbsent(key, k -> object());
    }

    /** Gets the child array at the given key, creating it if needed. */
    @SuppressWarnings("unchecked")
    public static List<Object> childArray(Map<String, Object> parent, String key) {
        return (List<Object>) parent.computeIfAbsent(key, k -> new ArrayList<>());
    }

    // ------------------------------------------------------------------------------------------------------

    /** Serializes a JSON tree with a 4-space indentation. */
    public static String dump(Object value) {
        StringBuilder sb = new StringBuilder();
        dump(sb, value, 4, 0);
        return sb.toString();
    }

    private static void dump(StringBuilder sb, Object value, int indentStep, int currentIndent) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append("{\n");
            int newIndent = currentIndent + indentStep;
            Iterator<? extends Map.Entry<?, ?>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<?, ?> entry = it.next();
                sb.append(" ".repeat(newIndent));
                appendString(sb, String.valueOf(entry.getKey()));
                sb.append(": ");
                dump(sb, entry.getValue(), indentStep, newIndent);
                if (it.hasNext())
                    sb.append(',');
                sb.append('\n');
            }
            sb.append(" ".repeat(currentIndent)).append('}');
        } else if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append("[\n");
            int newIndent = currentIndent + indentStep;
            for (int i = 0; i < list.size(); ++i) {
                sb.append(" ".repeat(newIndent));
                dump(sb, list.get(i), indentStep, newIndent);
                if (i < list.size() - 1)
                    sb.append(',');
                sb.append('\n');
            }
            sb.append(" ".repeat(currentIndent)).append(']');
        } else if (value instanceof String str) {
            appendString(sb, str);
        } else if (value instanceof Boolean || value instanceof Number) {
            sb.append(value);
        } else {
            appendString(sb, value.toString());
        }
    }

    private static void appendString(StringBuilder sb, String str) {
        sb.append('"');
        for (int i = 0; i < str.length(); ++i) {
            char c = str.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20)
                        sb.append(String.format("\\u%04x", (int) c));
                    else
                        sb.append(c);
                }
            }
        }
        sb.append('"');
    }
}
