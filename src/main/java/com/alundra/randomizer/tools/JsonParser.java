package com.alundra.randomizer.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small, dependency-free JSON parser. Besides standard JSON, it accepts {@code //} and {@code /* *}{@code /}
 * comments and trailing commas, which makes hand-written preset files more convenient.
 */
public final class JsonParser {
    private final String text;
    private int pos = 0;

    private JsonParser(String text) {
        this.text = text;
    }

    public static JsonNode parse(String text) {
        JsonParser parser = new JsonParser(text);
        parser.skipWhitespaceAndComments();
        JsonNode value = parser.parseValue();
        parser.skipWhitespaceAndComments();
        if (parser.pos != text.length())
            throw parser.error("Unexpected trailing characters");
        return value;
    }

    private RandomizerException error(String message) {
        int line = 1;
        int column = 1;
        for (int i = 0; i < Math.min(pos, text.length()); ++i) {
            if (text.charAt(i) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return new RandomizerException("Malformed json -> " + message + " at line " + line + ", column " + column);
    }

    private char peek() {
        if (pos >= text.length())
            throw error("Unexpected end of input");
        return text.charAt(pos);
    }

    private void expect(char c) {
        if (peek() != c)
            throw error("Expected '" + c + "' but found '" + peek() + "'");
        pos++;
    }

    private void skipWhitespaceAndComments() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '﻿') {
                pos++;
            } else if (c == '/' && pos + 1 < text.length() && text.charAt(pos + 1) == '/') {
                while (pos < text.length() && text.charAt(pos) != '\n')
                    pos++;
            } else if (c == '/' && pos + 1 < text.length() && text.charAt(pos + 1) == '*') {
                int end = text.indexOf("*/", pos + 2);
                if (end < 0)
                    throw error("Unterminated comment");
                pos = end + 2;
            } else {
                break;
            }
        }
    }

    private JsonNode parseValue() {
        char c = peek();
        switch (c) {
            case '{': return parseObject();
            case '[': return parseArray();
            case '"': return new JsonNode(JsonNode.Type.STRING, parseString());
            case 't': parseLiteral("true"); return new JsonNode(JsonNode.Type.BOOLEAN, Boolean.TRUE);
            case 'f': parseLiteral("false"); return new JsonNode(JsonNode.Type.BOOLEAN, Boolean.FALSE);
            case 'n': parseLiteral("null"); return new JsonNode(JsonNode.Type.NULL, null);
            default:
                if (c == '-' || (c >= '0' && c <= '9'))
                    return parseNumber();
                throw error("Unexpected character '" + c + "'");
        }
    }

    private void parseLiteral(String literal) {
        if (!text.startsWith(literal, pos))
            throw error("Invalid literal");
        pos += literal.length();
    }

    private JsonNode parseObject() {
        expect('{');
        Map<String, JsonNode> map = new LinkedHashMap<>();
        skipWhitespaceAndComments();
        if (peek() == '}') {
            pos++;
            return new JsonNode(JsonNode.Type.OBJECT, map);
        }
        while (true) {
            skipWhitespaceAndComments();
            if (peek() == '}') { // trailing comma
                pos++;
                break;
            }
            String key = parseString();
            skipWhitespaceAndComments();
            expect(':');
            skipWhitespaceAndComments();
            map.put(key, parseValue());
            skipWhitespaceAndComments();
            char c = peek();
            pos++;
            if (c == '}')
                break;
            if (c != ',')
                throw error("Expected ',' or '}' in object");
        }
        return new JsonNode(JsonNode.Type.OBJECT, map);
    }

    private JsonNode parseArray() {
        expect('[');
        List<JsonNode> list = new ArrayList<>();
        skipWhitespaceAndComments();
        if (peek() == ']') {
            pos++;
            return new JsonNode(JsonNode.Type.ARRAY, list);
        }
        while (true) {
            skipWhitespaceAndComments();
            if (peek() == ']') { // trailing comma
                pos++;
                break;
            }
            list.add(parseValue());
            skipWhitespaceAndComments();
            char c = peek();
            pos++;
            if (c == ']')
                break;
            if (c != ',')
                throw error("Expected ',' or ']' in array");
        }
        return new JsonNode(JsonNode.Type.ARRAY, list);
    }

    private String parseString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = peek();
            pos++;
            if (c == '"')
                break;
            if (c == '\\') {
                char escaped = peek();
                pos++;
                switch (escaped) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (pos + 4 > text.length())
                            throw error("Invalid unicode escape");
                        try {
                            sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        } catch (NumberFormatException e) {
                            throw error("Invalid unicode escape");
                        }
                        pos += 4;
                    }
                    default -> throw error("Invalid escape sequence '\\" + escaped + "'");
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private JsonNode parseNumber() {
        int start = pos;
        if (peek() == '-')
            pos++;
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-')
                pos++;
            else
                break;
        }
        try {
            return new JsonNode(JsonNode.Type.NUMBER, Double.parseDouble(text.substring(start, pos)));
        } catch (NumberFormatException e) {
            throw error("Invalid number");
        }
    }
}
