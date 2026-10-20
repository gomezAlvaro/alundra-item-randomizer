package com.alundra.randomizer.model;

import com.alundra.randomizer.tools.Json;
import com.alundra.randomizer.tools.RandomizerException;
import com.alundra.randomizer.tools.JsonNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A node of the logic graph, representing a zone that can be reached by the player.
 */
public class WorldNode {
    /** A unique identifier for the node */
    private final String id;
    /** All item sources contained inside the node */
    private final List<ItemSource> itemSources = new ArrayList<>();
    /** Hints that can be given to point to an item source inside this node */
    private final List<String> hints = new ArrayList<>();
    /** The region this node belongs to */
    private WorldRegion region = null;
    /**
     * If true, the item sources in this node shouldn't contain two instances of the same item. This will not be
     * strictly enforced, and might be bypassed if there is no other way to finish distributing the items
     */
    private boolean shouldHaveUniqueItems = false;
    /** The position of the node when exported to a visual graph (null if unspecified) */
    private int[] positionInGraph = null;

    public WorldNode(String id) {
        this.id = id;
    }

    public String id() { return id; }

    public List<ItemSource> itemSources() { return Collections.unmodifiableList(itemSources); }
    void addItemSource(ItemSource source) { itemSources.add(source); }

    void removeItemSource(ItemSource source) {
        if (!itemSources.remove(source))
            throw new RandomizerException("Could not remove item source from node '" + id + "' because it was not contained inside.");
    }

    public List<String> hints() { return hints; }

    public WorldRegion region() { return region; }
    void region(WorldRegion region) { this.region = region; }

    public boolean shouldHaveUniqueItems() { return shouldHaveUniqueItems; }
    public void shouldHaveUniqueItems(boolean value) { this.shouldHaveUniqueItems = value; }

    public int[] positionInGraph() { return positionInGraph; }

    public Map<String, Object> toJson() {
        Map<String, Object> json = Json.object();
        if (!hints.isEmpty())
            json.put("hints", new ArrayList<>(hints));
        json.put("shouldHaveUniqueItems", shouldHaveUniqueItems);
        if (positionInGraph != null)
            json.put("graphPos", List.of(positionInGraph[0], positionInGraph[1]));
        return json;
    }

    public static WorldNode fromJson(JsonNode json, String id) {
        WorldNode node = new WorldNode(id);
        for (Map.Entry<String, JsonNode> entry : Json.fields(json)) {
            JsonNode value = entry.getValue();
            switch (entry.getKey()) {
                case "hints" -> node.hints.addAll(Json.stringList(value));
                case "graphPos" -> node.positionInGraph = new int[]{value.get(0).asInt(), value.get(1).asInt()};
                case "shouldHaveUniqueItems" -> node.shouldHaveUniqueItems(value.asBoolean());
                default -> throw new RandomizerException("Unknown key '" + entry.getKey() + "' in WorldNode JSON");
            }
        }
        return node;
    }

    @Override
    public String toString() {
        return id;
    }
}
