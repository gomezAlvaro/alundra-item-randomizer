package com.alundra.randomizer.model;

import com.alundra.randomizer.tools.Json;
import com.alundra.randomizer.tools.RandomizerException;
import com.alundra.randomizer.tools.JsonNode;

import java.util.Map;

/**
 * A character (or sign) in the game that gives a hint to the player.
 */
public class HintSource {
    private String name = "";
    /** The node where the hint source is located, which needs to be reachable for the hint to be readable */
    private WorldNode node = null;
    private String text = "";

    public String name() { return name; }
    public void name(String value) { this.name = value; }

    public WorldNode node() { return node; }
    public void node(WorldNode node) { this.node = node; }

    public String text() { return text; }
    public void text(String value) { this.text = value; }

    public Map<String, Object> toJson() {
        Map<String, Object> json = Json.object();
        json.put("name", name);
        if (node != null)
            json.put("nodeId", node.id());
        json.put("text", text);
        return json;
    }

    public static HintSource fromJson(JsonNode json, RandomizerWorld world) {
        HintSource source = new HintSource();
        for (Map.Entry<String, JsonNode> entry : Json.fields(json)) {
            JsonNode value = entry.getValue();
            switch (entry.getKey()) {
                case "name" -> source.name(value.asText());
                case "nodeId" -> source.node(world.node(value.asText()));
                case "text" -> source.text(value.asText());
                default -> throw new RandomizerException("Unknown key '" + entry.getKey() + "' in HintSource JSON");
            }
        }
        return source;
    }
}
