package com.alundra.randomizer.model;

import com.alundra.randomizer.game.GameData;
import com.alundra.randomizer.game.Item;
import com.alundra.randomizer.tools.Json;
import com.alundra.randomizer.tools.RandomizerException;
import com.alundra.randomizer.tools.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A place where an item can be obtained (chest, NPC reward, shop slot...).
 */
public class ItemSource {
    /** The name used to describe this ItemSource */
    private String name = "";
    /** The item contained inside this ItemSource */
    private Item item = null;
    /** The item whose sprite is shown instead of the one of the contained item (null to show the latter) */
    private Item spriteItem = null;
    /** The item contained inside this ItemSource in the base game */
    private Item vanillaItem = null;
    /** The logic node containing this ItemSource */
    private WorldNode node = null;
    /** Addresses inside DATAS.BIN file where to put the contained item ID */
    private List<Long> addresses = new ArrayList<>();
    /** Addresses inside DATAS.BIN file where to put the contained item sprite ID */
    private List<Long> spriteAddresses = new ArrayList<>();
    /** Addresses inside DATAS.BIN file where to put the price of the contained item as a word */
    private List<Long> priceAddresses = new ArrayList<>();
    /** Address inside ALUN_CD.EXE file where to put the contained item ItemInfo address (0 if not handled) */
    private long merrickItemAddress = 0;
    /** Whether this item source can hold an item required for progression or not */
    private boolean canContainProgression = true;
    /**
     * If true, this ItemSource cannot contain "precious items" (items that would break the game if taken
     * repeatedly). This is usually set for ItemSources that can be taken an infinite amount of times.
     */
    private boolean forbidPreciousItems = false;
    /** Hints which can be used to subtly point at this ItemSource */
    private final List<String> hints = new ArrayList<>();

    public String name() { return name; }
    public void name(String name) { this.name = name; }

    public String prettyName() {
        if (vanillaItem != null)
            return name + " (" + vanillaItem.name() + ")";
        return name;
    }

    public Item item() { return item; }
    public void item(Item item) { this.item = item; }

    public Item spriteItem() { return spriteItem; }
    public void spriteItem(Item item) { this.spriteItem = item; }

    /** A source is empty if it contains no item, but not if it contains ITEM_NONE */
    public boolean isEmpty() { return item == null; }

    public Item vanillaItem() { return vanillaItem; }
    public void vanillaItem(Item item) { this.vanillaItem = item; }

    public WorldNode node() { return node; }

    public void node(WorldNode newNode) {
        if (node != null)
            node.removeItemSource(this);
        node = newNode;
        node.addItemSource(this);
    }

    public List<String> hints() { return hints; }
    public void addHint(String hint) { hints.add(hint); }

    public boolean canContainProgression() { return canContainProgression; }
    public void canContainProgression(boolean value) { this.canContainProgression = value; }

    public List<Long> addresses() { return addresses; }
    public List<Long> spriteAddresses() { return spriteAddresses; }
    public List<Long> priceAddresses() { return priceAddresses; }

    public long merrickItemAddress() { return merrickItemAddress; }
    public void merrickItemAddress(long addr) { this.merrickItemAddress = addr; }

    public boolean forbidPreciousItems() { return forbidPreciousItems; }
    public void forbidPreciousItems(boolean value) { this.forbidPreciousItems = value; }

    public int fillPriority() {
        int priority = 0;
        if (node.shouldHaveUniqueItems())
            priority += 2;
        if (forbidPreciousItems)
            priority += 1;
        return priority;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> json = Json.object();
        json.put("name", name);
        json.put("nodeId", node.id());
        if (!addresses.isEmpty())
            json.put("address", addressesToJson(addresses));
        if (!spriteAddresses.isEmpty())
            json.put("spriteAddress", addressesToJson(spriteAddresses));
        if (!priceAddresses.isEmpty())
            json.put("priceAddress", addressesToJson(priceAddresses));
        if (merrickItemAddress != 0)
            json.put("merrickItemAddress", Long.toHexString(merrickItemAddress).toUpperCase());
        if (!hints.isEmpty())
            json.put("hints", new ArrayList<>(hints));
        if (vanillaItem != null)
            json.put("vanillaItem", vanillaItem.name());
        if (forbidPreciousItems)
            json.put("forbidPreciousItems", true);
        return json;
    }

    public static ItemSource fromJson(JsonNode json, GameData gameData, RandomizerWorld world) {
        ItemSource source = new ItemSource();
        for (Map.Entry<String, JsonNode> entry : Json.fields(json)) {
            JsonNode value = entry.getValue();
            switch (entry.getKey()) {
                case "name" -> source.name(value.asText());
                case "nodeId" -> source.node(world.node(value.asText()));
                case "address" -> source.addresses = parseAddresses(value);
                case "spriteAddress" -> source.spriteAddresses = parseAddresses(value);
                case "priceAddress" -> source.priceAddresses = parseAddresses(value);
                case "merrickItemAddress" -> source.merrickItemAddress(parseAddresses(value).get(0));
                case "hints" -> source.hints.addAll(Json.stringList(value));
                case "vanillaItem" -> source.vanillaItem(gameData.item(value.asText()));
                case "forbidPreciousItems" -> source.forbidPreciousItems(value.asBoolean());
                default -> throw new RandomizerException("Unknown key '" + entry.getKey() + "' in ItemSource JSON");
            }
        }
        return source;
    }

    static List<Long> parseAddresses(JsonNode json) {
        List<Long> addresses = new ArrayList<>();
        if (json.isArray()) {
            for (JsonNode element : json)
                addresses.add(Long.parseLong(element.asText().trim(), 16));
        } else {
            addresses.add(Long.parseLong(json.asText().trim(), 16));
        }
        return addresses;
    }

    static Object addressesToJson(List<Long> addresses) {
        List<String> strings = new ArrayList<>();
        for (long addr : addresses)
            strings.add(Long.toHexString(addr).toUpperCase());
        if (strings.size() == 1)
            return strings.get(0);
        return strings;
    }

    @Override
    public String toString() {
        return prettyName();
    }
}
