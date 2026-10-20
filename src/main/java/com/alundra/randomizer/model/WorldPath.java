package com.alundra.randomizer.model;

import com.alundra.randomizer.game.GameData;
import com.alundra.randomizer.game.Item;
import com.alundra.randomizer.tools.Json;
import com.alundra.randomizer.tools.RandomizerException;
import com.alundra.randomizer.tools.JsonNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * An edge of the logic graph, connecting two nodes with optional requirements (items, other visited nodes).
 */
public class WorldPath {
    /** Node where the path starts from */
    private WorldNode fromNode;
    /** Node where the path ends up */
    private WorldNode toNode;
    /** If true, path can be taken both ways (including from destination to origin) */
    private boolean twoWay = false;
    /** Items required to cross the path (an item can be present several times to require several instances) */
    private final List<Item> requiredItems = new ArrayList<>();
    /** Nodes which must have been visited before crossing the path */
    private final List<WorldNode> requiredNodes = new ArrayList<>();
    /**
     * The weight of the path, a higher value means it has a higher chance to be selected by the WorldShuffler to be
     * opened (by placing required items associated with it somewhere inside the world).
     */
    private int weight = 1;

    public WorldPath() {
    }

    public WorldPath(WorldNode from, WorldNode to) {
        this.fromNode = from;
        this.toNode = to;
    }

    public WorldNode origin() { return fromNode; }
    public void origin(WorldNode node) { this.fromNode = node; }

    public WorldNode destination() { return toNode; }
    public void destination(WorldNode node) { this.toNode = node; }

    public boolean isTwoWay() { return twoWay; }
    public void isTwoWay(boolean value) { this.twoWay = value; }

    public List<Item> requiredItems() { return requiredItems; }
    public void addRequiredItem(Item item) { requiredItems.add(item); }

    /** @return required items with their quantity, sorted by item ID */
    public Map<Item, Integer> requiredItemsAndQuantity() {
        Map<Item, Integer> quantities = new TreeMap<>(Comparator.comparingInt(Item::id));
        for (Item item : requiredItems)
            quantities.merge(item, 1, Integer::sum);
        return quantities;
    }

    public List<WorldNode> requiredNodes() { return requiredNodes; }

    public int weight() { return weight; }
    public void weight(int weight) { this.weight = weight; }

    public Map<String, Object> toJson() {
        Map<String, Object> json = Json.object();
        json.put("fromId", fromNode.id());
        json.put("toId", toNode.id());
        if (twoWay)
            json.put("twoWay", true);
        if (weight > 1)
            json.put("weight", weight);
        if (!requiredItems.isEmpty()) {
            List<Object> items = new ArrayList<>();
            for (Item item : requiredItems)
                items.add(item.name());
            json.put("requiredItems", items);
        }
        if (!requiredNodes.isEmpty()) {
            List<Object> nodes = new ArrayList<>();
            for (WorldNode node : requiredNodes)
                nodes.add(node.id());
            json.put("requiredNodes", nodes);
        }
        return json;
    }

    public static WorldPath fromJson(JsonNode json, Map<String, WorldNode> nodes, GameData gameData) {
        WorldPath path = new WorldPath();
        for (Map.Entry<String, JsonNode> entry : Json.fields(json)) {
            JsonNode value = entry.getValue();
            switch (entry.getKey()) {
                case "fromId" -> path.origin(findNode(nodes, value.asText()));
                case "toId" -> path.destination(findNode(nodes, value.asText()));
                case "twoWay" -> path.isTwoWay(value.asBoolean());
                case "weight" -> path.weight(value.asInt());
                case "requiredNodes" -> {
                    for (JsonNode nodeId : value)
                        path.requiredNodes.add(findNode(nodes, nodeId.asText()));
                }
                case "requiredItems" -> {
                    for (JsonNode itemJson : value) {
                        Item requiredItem;
                        int quantity;
                        if (itemJson.isObject()) {
                            requiredItem = gameData.item(itemJson.get("name").asText());
                            quantity = itemJson.get("quantity").asInt();
                        } else {
                            requiredItem = gameData.item(itemJson.asText());
                            quantity = 1;
                        }
                        for (int i = 0; i < quantity; ++i)
                            path.requiredItems.add(requiredItem);
                    }
                }
                default -> throw new RandomizerException("Unknown key '" + entry.getKey() + "' in WorldPath JSON");
            }
        }
        return path;
    }

    private static WorldNode findNode(Map<String, WorldNode> nodes, String id) {
        WorldNode node = nodes.get(id);
        if (node == null)
            throw new RandomizerException("Unknown node '" + id + "' referenced in WorldPath JSON");
        return node;
    }

    @Override
    public String toString() {
        return fromNode.id() + " --> " + toNode.id();
    }
}
