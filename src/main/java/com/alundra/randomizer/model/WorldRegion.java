package com.alundra.randomizer.model;

import com.alundra.randomizer.tools.Json;
import com.alundra.randomizer.tools.RandomizerException;
import com.alundra.randomizer.tools.JsonNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A group of nodes representing a concrete location inside the game. Used to group item sources inside the spoiler
 * log, and to generate hints.
 */
public class WorldRegion {
    /** The name of the region (as seen in the spoiler log) */
    private String name = "";
    /** The nodes contained inside that region */
    private final List<WorldNode> nodes = new ArrayList<>();
    /** The name of the region (as described in hints) */
    private String hintName = "";
    /** If true, a hint can say if exploring this region is necessary to complete the seed */
    private boolean canBeHintedAsRequired = false;

    public String name() { return name; }
    public void name(String value) { this.name = value; }

    public List<WorldNode> nodes() { return Collections.unmodifiableList(nodes); }

    public void addNode(WorldNode node) {
        nodes.add(node);
        node.region(this);
    }

    public String hintName() {
        if (hintName.isEmpty())
            return "in " + name;
        return hintName;
    }

    public void hintName(String value) { this.hintName = value; }

    public boolean canBeHintedAsRequired() { return canBeHintedAsRequired; }
    public void canBeHintedAsRequired(boolean value) { this.canBeHintedAsRequired = value; }

    public List<ItemSource> itemSources() {
        List<ItemSource> sources = new ArrayList<>();
        for (WorldNode node : nodes)
            sources.addAll(node.itemSources());
        return sources;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> json = Json.object();
        json.put("name", name);
        json.put("hintName", hintName);
        json.put("canBeHintedAsRequired", canBeHintedAsRequired);
        List<Object> nodeIds = new ArrayList<>();
        for (WorldNode node : nodes)
            nodeIds.add(node.id());
        json.put("nodeIds", nodeIds);
        return json;
    }

    public static WorldRegion fromJson(JsonNode json, RandomizerWorld world) {
        WorldRegion region = new WorldRegion();
        for (Map.Entry<String, JsonNode> entry : Json.fields(json)) {
            JsonNode value = entry.getValue();
            switch (entry.getKey()) {
                case "name" -> region.name(value.asText());
                case "hintName" -> region.hintName(value.asText());
                case "canBeHintedAsRequired" -> region.canBeHintedAsRequired(value.asBoolean());
                case "nodeIds" -> {
                    for (JsonNode nodeId : value)
                        region.addNode(world.node(nodeId.asText()));
                }
                default -> throw new RandomizerException("Unknown key '" + entry.getKey() + "' in WorldRegion JSON");
            }
        }
        return region;
    }

    @Override
    public String toString() {
        return name;
    }
}
