package com.alundra.randomizer.game;

import com.alundra.randomizer.tools.RandomizerException;
import com.alundra.randomizer.tools.JsonNode;

import java.util.Map;

import static com.alundra.randomizer.tools.Json.fields;

/**
 * An item from the game (weapon, key item, consumable...).
 */
public class Item {
    /** The in-game ID of the item */
    private final int id;
    private String name = "";
    /** The maximum amount of this item the player can carry at any given point */
    private int maxQuantity = 1;
    /** The value of this item in shops */
    private int goldValue = 0;
    /** The ID of the slot where this item goes in inventory */
    private int slotId = 0;
    /** The tier of this item compared to other items from the same slot */
    private int tier = 0;
    /**
     * If true, this item is considered as precious and cannot be put inside ItemSources which forbid precious
     * items. This is used on ItemSources that can be taken an infinite amount of times to avoid putting
     * abusable items such as Life Vessels or Magic Seeds.
     */
    private boolean precious = false;

    public Item(int id) {
        this.id = id;
    }

    public int id() { return id; }

    public String name() { return name; }
    public void name(String name) { this.name = name; }

    public int maxQuantity() { return maxQuantity; }
    public void maxQuantity(int value) { this.maxQuantity = value; }

    public int goldValue() { return goldValue; }
    public void goldValue(int value) { this.goldValue = value; }

    public int slotId() { return slotId; }
    public void slotId(int value) { this.slotId = value; }

    public int tier() { return tier; }
    public void tier(int value) { this.tier = value; }

    public boolean isPrecious() { return precious; }
    public void isPrecious(boolean value) { this.precious = value; }

    public void applyJson(JsonNode json) {
        for (Map.Entry<String, JsonNode> entry : fields(json)) {
            JsonNode value = entry.getValue();
            switch (entry.getKey()) {
                case "name" -> name(value.asText());
                case "maxQuantity" -> maxQuantity(value.asInt());
                case "goldValue" -> goldValue(value.asInt());
                case "precious" -> isPrecious(value.asBoolean());
                case "slot" -> slotId(value.asInt());
                case "tier" -> tier(value.asInt());
                default -> throw new RandomizerException("Unknown key '" + entry.getKey() + "' in Item JSON");
            }
        }
    }

    public Map<String, Object> toJson() {
        Map<String, Object> json = com.alundra.randomizer.tools.Json.object();
        json.put("name", name);
        json.put("slot", slotId);
        json.put("tier", tier);
        json.put("maxQuantity", maxQuantity);
        json.put("goldValue", goldValue);
        if (precious)
            json.put("precious", true);
        return json;
    }

    @Override
    public String toString() {
        return name;
    }
}
