package com.alundra.randomizer.tools;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * An immutable parsed JSON value (see {@link JsonParser}). Objects keep their keys in document order.
 * Iterating over a node iterates over array elements (or object values).
 */
public final class JsonNode implements Iterable<JsonNode> {
    public enum Type { OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, NULL }

    private final Type type;
    private final Object value;

    JsonNode(Type type, Object value) {
        this.type = type;
        this.value = value;
    }

    public Type type() { return type; }

    public boolean isObject() { return type == Type.OBJECT; }
    public boolean isArray() { return type == Type.ARRAY; }
    public boolean isTextual() { return type == Type.STRING; }
    public boolean isNumber() { return type == Type.NUMBER; }
    public boolean isBoolean() { return type == Type.BOOLEAN; }
    public boolean isNull() { return type == Type.NULL; }

    @SuppressWarnings("unchecked")
    private Map<String, JsonNode> objectMap() {
        if (type != Type.OBJECT)
            throw new RandomizerException("Expected a JSON object but found " + describe());
        return (Map<String, JsonNode>) value;
    }

    @SuppressWarnings("unchecked")
    private List<JsonNode> arrayList() {
        if (type != Type.ARRAY)
            throw new RandomizerException("Expected a JSON array but found " + describe());
        return (List<JsonNode>) value;
    }

    /** @return the value at the given key of an object, or null if absent */
    public JsonNode get(String key) {
        return objectMap().get(key);
    }

    /** @return the element at the given index of an array */
    public JsonNode get(int index) {
        return arrayList().get(index);
    }

    public boolean has(String key) {
        return type == Type.OBJECT && objectMap().containsKey(key);
    }

    public int size() {
        return switch (type) {
            case OBJECT -> objectMap().size();
            case ARRAY -> arrayList().size();
            default -> 0;
        };
    }

    /** @return the (key, value) pairs of an object, in document order */
    public Iterator<Map.Entry<String, JsonNode>> fields() {
        return Collections.unmodifiableMap(objectMap()).entrySet().iterator();
    }

    @Override
    public Iterator<JsonNode> iterator() {
        return switch (type) {
            case OBJECT -> Collections.unmodifiableCollection(objectMap().values()).iterator();
            case ARRAY -> Collections.unmodifiableList(arrayList()).iterator();
            default -> Collections.emptyIterator();
        };
    }

    public String asText() {
        return switch (type) {
            case STRING -> (String) value;
            case NUMBER -> numberToString();
            case BOOLEAN -> value.toString();
            case NULL -> "null";
            default -> throw new RandomizerException("Expected a JSON string but found " + describe());
        };
    }

    public long asLong() {
        if (type == Type.NUMBER) {
            double d = (Double) value;
            if (d != Math.rint(d))
                throw new RandomizerException("Expected an integer but found " + numberToString());
            return (long) d;
        }
        if (type == Type.STRING) {
            try {
                return Long.parseLong(((String) value).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        throw new RandomizerException("Expected a number but found " + describe());
    }

    public int asInt() {
        return (int) asLong();
    }

    public boolean asBoolean() {
        if (type == Type.BOOLEAN)
            return (Boolean) value;
        throw new RandomizerException("Expected a boolean but found " + describe());
    }

    private String numberToString() {
        double d = (Double) value;
        if (d == Math.rint(d) && Math.abs(d) < 1e15)
            return Long.toString((long) d);
        return Double.toString(d);
    }

    private String describe() {
        return switch (type) {
            case STRING -> "string \"" + value + "\"";
            case NUMBER -> "number " + numberToString();
            default -> type.name().toLowerCase();
        };
    }

    @Override
    public String toString() {
        return switch (type) {
            case STRING -> "\"" + value + "\"";
            case NUMBER -> numberToString();
            default -> String.valueOf(value);
        };
    }
}
